#!/bin/bash
# 2JO prod cloud-init — 첫 기동 때 한 번 돈다.
#
# 원칙: 여기서 실패해도 인스턴스는 살아 있어야 한다.
#       SSH 가 없어서 디버깅 수단이 SSM 뿐인데, cloud-init 이 죽으면
#       원인을 볼 방법도 같이 사라진다.
#
# 로그: /var/log/cloud-init-output.log (SSM 으로 읽는다)
# 설계 근거: network-compute.md §3.6

set -uo pipefail # -e 를 빼는 것이 의도다. 아래에서 단계별로 직접 판정한다

APP_DIR="${app_dir}"
CONFIG_BUCKET="${config_bucket}"
SSM_PREFIX="${ssm_parameter_prefix}"
AWS_REGION="${aws_region}"
SWAP_GB="${swap_size_gb}"

log() { echo "[2jo-init] $*"; }
fail() {
  log "FAILED: $*"
  log "인스턴스는 계속 살아 있다. SSM 으로 접속해 이 로그를 확인할 것."
  exit 0
}

# ── 0. 인스턴스가 자기 자신에 대해 아는 것 ───────────────────────────────
# deploy.sh · backup.sh 가 나중에 SSM 으로 불릴 때는 이 값들을 알 방법이 없다.
# terraform 이 아는 것을 여기서 한 번 파일로 떨어뜨린다.
# 시크릿은 넣지 않는다 — 그건 SSM SecureString 에서 온다.
log "인스턴스 설정 기록: /etc/2jo.conf"
cat >/etc/2jo.conf <<CONF
APP_DIR=$APP_DIR
BUCKET=$CONFIG_BUCKET
AWS_REGION=$AWS_REGION
SSM_PREFIX=$SSM_PREFIX
CONF
chmod 644 /etc/2jo.conf

# ── 1. 도커 ──────────────────────────────────────────────────────────────
log "도커 설치"
dnf install -y docker || fail "docker 설치"
systemctl enable --now docker || fail "docker 기동"

# AL2023 에는 compose 플러그인이 패키지로 없다. 플러그인 경로에 직접 받는다.
log "docker compose 플러그인 설치"
install -d /usr/libexec/docker/cli-plugins
COMPOSE_URL="https://github.com/docker/compose/releases/latest/download/docker-compose-linux-aarch64"
curl -fsSL "$COMPOSE_URL" -o /usr/libexec/docker/cli-plugins/docker-compose ||
  fail "compose 플러그인 내려받기"
chmod +x /usr/libexec/docker/cli-plugins/docker-compose

# ── 2. 스왑 ──────────────────────────────────────────────────────────────
# 4 GiB 에 컨테이너 9종(~2.9 GiB)이 올라간다. 여유가 크지 않아 안전망을 둔다.
# 추가 비용은 없다 — 루트 EBS 안의 파일이다.
if [ ! -f /swapfile ]; then
  log "스왑파일 ${swap_size_gb}GB 생성"
  dd if=/dev/zero of=/swapfile bs=1M count=$((SWAP_GB * 1024)) status=none || fail "스왑 생성"
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null || fail "mkswap"
  swapon /swapfile || fail "swapon"
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
  # 스왑을 쓰긴 쓰되 최대한 늦게 쓴다. 0 이면 OOM 때 안전망이 안 열린다.
  echo 'vm.swappiness=10' >/etc/sysctl.d/99-2jo-swap.conf
  sysctl -p /etc/sysctl.d/99-2jo-swap.conf >/dev/null
fi

# ── 3. 설정 번들 ─────────────────────────────────────────────────────────
# user_data 에 인라인으로 박지 않는 이유: 설정을 고칠 때마다 인스턴스가
# 재생성된다. S3 에 두면 이후에는 SSM 으로 재동기화만 하면 된다.
log "설정 번들 동기화: s3://$CONFIG_BUCKET/config/"
install -d "$APP_DIR"
# .env 계열은 S3 에 없다. --delete 대상에서 빼지 않으면 재동기화할 때마다
# 시크릿과 이미지 태그가 지워진다.
aws s3 sync "s3://$CONFIG_BUCKET/config/" "$APP_DIR/" \
  --region "$AWS_REGION" --delete \
  --exclude ".env" --exclude ".env.image" ||
  fail "설정 번들 동기화"

# ── 4. 시크릿 ────────────────────────────────────────────────────────────
# 값은 terraform 이 만들지 않는다(tfstate 가 평문이다).
# 사람이 SecureString 으로 넣어둔 것을 여기서 꺼낸다.
if [ -x "$APP_DIR/scripts/fetch-secrets.sh" ]; then
  log "시크릿 조회"
  export SSM_PREFIX AWS_REGION APP_DIR
  "$APP_DIR/scripts/fetch-secrets.sh" || fail "fetch-secrets.sh"
else
  log "fetch-secrets.sh 없음 — 건너뛴다"
fi

# ── 5. 컨테이너는 여기서 띄우지 않는다 ───────────────────────────────────
# 첫 기동 시점에는 백엔드 이미지가 아직 ECR 에 없다. 배포 워크플로가 이미지를
# 올리고 deploy.sh 가 compose up 을 한다.
#
# 여기서 일부만 띄우면 "반쯤 뜬 상태"가 생기고, 그 상태를 전제로 한 디버깅이
# 필요해진다. 서버는 받을 준비만 해두고 기동은 배포 한 곳에서만 한다.
COMPOSE_FILE="$APP_DIR/compose/compose.yml"
if [ -f "$COMPOSE_FILE" ]; then
  log "compose 파일 확인됨 — 기동은 첫 배포(deploy.sh)가 한다"
else
  log "compose 파일 없음 — 설정 번들이 아직 S3 에 없다"
fi

log "완료"
