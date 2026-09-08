#!/bin/bash
# 2JO prod cloud-init — 첫 기동 때 한 번 돈다.
#
# 원칙: 여기서 실패해도 인스턴스는 살아 있어야 한다.
#       여기서 배포용 공개키를 심기 때문에, 이 스크립트가 죽으면 배포도
#       디버깅도 같이 막힌다. 그래서 단계별로 판정하고 exit 0 으로 끝낸다.
#
# 로그: /var/log/cloud-init-output.log (콘솔 시스템 로그 또는 SSH 로 읽는다)
# 설계 근거: network-compute.md §3.6

set -uo pipefail # -e 를 빼는 것이 의도다. 아래에서 단계별로 직접 판정한다

APP_DIR="${app_dir}"
CONFIG_BUCKET="${config_bucket}"
AWS_REGION="${aws_region}"
SWAP_GB="${swap_size_gb}"
SSH_USER="ec2-user"

log() { echo "[2jo-init] $*"; }
fail() {
  log "FAILED: $*"
  log "인스턴스는 계속 살아 있다. 콘솔 시스템 로그에서 이 줄을 확인할 것."
  exit 0
}

# ── 0. 인스턴스가 자기 자신에 대해 아는 것 ───────────────────────────────
# deploy.sh · backup.sh 가 나중에 SSH 로 불릴 때는 이 값들을 알 방법이 없다.
# terraform 이 아는 것을 여기서 한 번 파일로 떨어뜨린다.
# 시크릿은 넣지 않는다 — 그건 배포 워크플로가 .env 로 넣는다.
log "인스턴스 설정 기록: /etc/2jo.conf"
cat >/etc/2jo.conf <<CONF
APP_DIR=$APP_DIR
BUCKET=$CONFIG_BUCKET
AWS_REGION=$AWS_REGION
CONF
chmod 644 /etc/2jo.conf

# ── 1. 배포용 SSH 공개키 ─────────────────────────────────────────────────
# 키를 둘로 나눈다. 사람 접속용은 key_name 으로 AWS 가 심고, 배포용은 여기서
# 심는다. 한쪽이 유출돼도 한쪽만 교체하면 된다.
#
# 아래 단계가 실패해도 이건 남아야 배포와 조사가 가능하다 — 그래서 맨 앞이다.
log "배포용 공개키 등록"
SSH_HOME="/home/$SSH_USER/.ssh"
install -d -m 700 -o "$SSH_USER" -g "$SSH_USER" "$SSH_HOME" || fail ".ssh 디렉터리 생성"
# 작은따옴표가 의도다. 이건 셸 변수가 아니라 terraform templatefile 이
# 렌더링하는 자리표시자이고, 값(공개키)에 셸이 손대면 안 된다.
# shellcheck disable=SC2016
DEPLOY_KEY='${deploy_public_key}'
if ! grep -qxF "$DEPLOY_KEY" "$SSH_HOME/authorized_keys" 2>/dev/null; then
  echo "$DEPLOY_KEY" >>"$SSH_HOME/authorized_keys"
fi
chmod 600 "$SSH_HOME/authorized_keys"
chown "$SSH_USER:$SSH_USER" "$SSH_HOME/authorized_keys"

# ── 2. 도커 ──────────────────────────────────────────────────────────────
log "도커 설치"
dnf install -y docker || fail "docker 설치"
systemctl enable --now docker || fail "docker 기동"

# AL2023 에는 compose 플러그인이 패키지로 없다. 플러그인 경로에 직접 받는다.
log "docker compose 플러그인 설치"
install -d /usr/libexec/docker/cli-plugins
COMPOSE_URL="https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64"
curl -fsSL "$COMPOSE_URL" -o /usr/libexec/docker/cli-plugins/docker-compose ||
  fail "compose 플러그인 내려받기"
chmod +x /usr/libexec/docker/cli-plugins/docker-compose

# 배포가 sudo 없이 돌게 한다.
#
# 주의: 이건 보안 이득이 아니다. 도커 소켓 접근 권한은 root 와 사실상 동등하다.
#       NOPASSWD sudo 설정을 하나 안 만들어도 되는 운영상 단순함 때문이다.
log "$SSH_USER 를 docker 그룹에 추가"
usermod -aG docker "$SSH_USER" || fail "docker 그룹 추가"

# systemd user unit 을 로그인 세션 없이 돌리려면 linger 가 필요하다.
# 백업 타이머가 이 위에서 돈다(이슈 #159). 유닛 파일 자체는 여기서 만들지
# 않는다 — user_data 는 고쳐도 다시 실행되지 않아 스케줄을 바꿀 수 없다.
# 설치는 deploy.sh 가 S3 설정 번들에서 받아서 한다.
log "$SSH_USER linger 활성화 (user timer 용)"
loginctl enable-linger "$SSH_USER" || fail "enable-linger"

# ── 3. 스왑 ──────────────────────────────────────────────────────────────
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

# ── 4. 설정 번들 ─────────────────────────────────────────────────────────
# user_data 에 인라인으로 박지 않는 이유: 설정을 고칠 때마다 인스턴스가
# 재생성된다. S3 에 두면 이후에는 배포 스크립트가 재동기화만 하면 된다.
log "설정 번들 동기화: s3://$CONFIG_BUCKET/config/"
# 소유자를 ec2-user 로 준다. deploy.sh 가 이 아래에 .env 를 쓰고
# .deploy.lock 을 잡아야 하는데, 그게 sudo 없이 되려면 소유권이 필요하다.
install -d -o "$SSH_USER" -g "$SSH_USER" "$APP_DIR"
# node_exporter 가 여기를 바인드 마운트한다. 미리 만들지 않으면 도커가
# root 소유로 만들어버리고, ec2-user 로 도는 backup.sh 가 쓰지 못한다.
install -d -m 755 -o "$SSH_USER" -g "$SSH_USER" "$APP_DIR/metrics"
# .env 계열은 S3 에 없다. --delete 대상에서 빼지 않으면 재동기화할 때마다
# 시크릿과 이미지 태그가 지워진다.
aws s3 sync "s3://$CONFIG_BUCKET/config/" "$APP_DIR/" \
  --region "$AWS_REGION" --delete \
  --exclude ".env" --exclude ".env.image" ||
  fail "설정 번들 동기화"
chown -R "$SSH_USER:$SSH_USER" "$APP_DIR"

# S3 는 실행 비트를 보존하지 않는다. 동기화해 온 스크립트는 항상 644 로
# 떨어지므로 여기서 다시 세운다 — 안 하면 deploy.sh 가 "Permission denied" 다.
chmod +x "$APP_DIR"/scripts/*.sh 2>/dev/null || true

# ── 5. 시크릿은 여기서 만들지 않는다 ────────────────────────────────────
# .env 는 배포 워크플로가 GitHub Secrets 에서 조립해 SSH stdin 으로 넣는다.
# terraform 도 cloud-init 도 시크릿 값을 보지 않는다.

# ── 6. 컨테이너는 여기서 띄우지 않는다 ───────────────────────────────────
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
