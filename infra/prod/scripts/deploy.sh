#!/usr/bin/env bash
# 배포 — 설정 동기화 + (인자가 있으면) 이미지 교체.
#
#   deploy.sh <이미지 참조>   이미지 교체 + 설정 동기화
#   deploy.sh                 설정만 동기화 (지금 도는 이미지 유지)
#
# 한 스크립트에 모으는 이유: 따로 배포하면 "compose 는 새 것, 이미지는 옛 것"
# 같은 조합이 생긴다.
#
# 종료 코드
#   0   성공
#   1   실패 · 롤백 대상 없음(최초 배포)
#   2   실패 · 롤백 성공 (서비스는 정상)
#   3   실패 · 롤백도 실패 (즉시 확인 필요)
#   75  다른 배포가 진행 중
#
# 설계 근거: cd.md §3 · §4
set -Eeuo pipefail

# 인스턴스가 자기 자신에 대해 아는 것(버킷·리전 등). cloud-init 이 쓴다.
CONF=/etc/2jo.conf
# shellcheck source=/dev/null
[ -r "$CONF" ] && . "$CONF"

APP_DIR="${APP_DIR:-/opt/2jo}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
BUCKET="${BUCKET:?BUCKET 을 모른다 — /etc/2jo.conf 를 확인할 것}"
PROJECT="${COMPOSE_PROJECT:-twojo}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-90}"

TARGET_IMAGE="${1:-}"

log() { echo "[deploy] $*"; }

# ── 1. 동시 실행 차단 ─────────────────────────────────────────────────────
# Actions 의 concurrency 는 워크플로만 막는다. 사람이 SSM 으로 직접 부르는
# 경로는 여기서 막힌다.
exec 200>/var/lock/2jo-deploy.lock
if ! flock -n 200; then
  log "다른 배포가 진행 중이다"
  exit 75
fi

# ── compose 헬퍼 ─────────────────────────────────────────────────────────
compose_files=(-f "$APP_DIR/compose/compose.yml")
# 모니터링 스택은 있으면 같이 올린다. 메모리가 부족해 내려둔 상태일 수 있다.
[ -f "$APP_DIR/compose/compose.monitoring.yml" ] &&
  compose_files+=(-f "$APP_DIR/compose/compose.monitoring.yml")

dc() {
  docker compose "${compose_files[@]}" \
    --env-file "$APP_DIR/.env" \
    --env-file "$APP_DIR/.env.image" \
    "$@"
}

container_of() {
  docker ps -q \
    --filter "label=com.docker.compose.project=${PROJECT}" \
    --filter "label=com.docker.compose.service=$1"
}

# ── 2. 롤백 대상 = 지금 실제로 도는 이미지 ────────────────────────────────
# 파일에 기록해 두는 방식을 쓰지 않는다. 파일은 기록이지 사실이 아니다 —
# 누가 수동으로 무엇을 했든 지금 도는 컨테이너가 정답이다.
prev_image=""
prev_cid="$(container_of backend || true)"
if [ -n "$prev_cid" ]; then
  prev_image="$(docker inspect -f '{{.Config.Image}}' "$prev_cid")"
  log "현재 이미지: $prev_image"
else
  log "실행 중인 backend 가 없다 (최초 배포로 본다)"
fi

# ── 3. 설정 번들 ─────────────────────────────────────────────────────────
# .env 계열은 S3 에 없으므로 --delete 대상에서 빼야 한다.
# 빼지 않으면 방금 받은 시크릿과 이미지 태그가 이 줄에서 지워진다.
log "설정 번들 동기화"
aws s3 sync "s3://${BUCKET}/config/" "$APP_DIR/" \
  --region "$AWS_REGION" --delete \
  --exclude ".env" --exclude ".env.image"

# ── 4. 시크릿 ────────────────────────────────────────────────────────────
log "시크릿 조회"
export APP_DIR AWS_REGION
[ -n "${SSM_PREFIX:-}" ] && export SSM_PREFIX
"$APP_DIR/scripts/fetch-secrets.sh"

# ── 5. 이미지 ────────────────────────────────────────────────────────────
if [ -n "$TARGET_IMAGE" ]; then
  registry="${TARGET_IMAGE%%/*}"
  log "ECR 로그인: $registry"
  aws ecr get-login-password --region "$AWS_REGION" |
    docker login --username AWS --password-stdin "$registry"

  log "이미지 pull: $TARGET_IMAGE"
  docker pull "$TARGET_IMAGE"
  printf 'BACKEND_IMAGE=%s\n' "$TARGET_IMAGE" >"$APP_DIR/.env.image"
elif [ -n "$prev_image" ]; then
  # 설정 전용 모드 — 지금 도는 이미지를 그대로 유지한다.
  printf 'BACKEND_IMAGE=%s\n' "$prev_image" >"$APP_DIR/.env.image"
else
  log "배포할 이미지도, 실행 중인 이미지도 없다"
  exit 1
fi

# ── 6. 교체 ──────────────────────────────────────────────────────────────
log "컨테이너 기동"
dc up -d

# ── 7. 설정 반영 ─────────────────────────────────────────────────────────
# 바인드 마운트한 설정 파일이 바뀌어도 컨테이너는 재시작되지 않는다.
# up -d 만으로는 Caddyfile·prometheus.yml 변경이 반영되지 않는다.
# 컨테이너가 아직 없을 수 있으므로 실패는 무시한다.
dc exec -T caddy caddy reload --config /etc/caddy/Caddyfile 2>/dev/null ||
  log "caddy reload 건너뜀"
dc exec -T prometheus kill -HUP 1 2>/dev/null ||
  log "prometheus reload 건너뜀"

# ── 8. 판정 ──────────────────────────────────────────────────────────────
# compose 에 정의한 헬스체크 결과를 읽는다. 별도로 curl 을 또 때리면
# 판정 기준이 두 벌이 되어 어긋날 수 있다.
wait_healthy() {
  local timeout="$1" waited=0 cid status
  while [ "$waited" -lt "$timeout" ]; do
    cid="$(container_of backend || true)"
    if [ -n "$cid" ]; then
      status="$(docker inspect \
        -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid")"
      case "$status" in
        healthy) return 0 ;;
        none)
          log "경고: backend 에 헬스체크가 없다 — 판정하지 못하고 통과시킨다"
          return 0
          ;;
      esac
    fi
    sleep 5
    waited=$((waited + 5))
  done
  return 1
}

if wait_healthy "$HEALTH_TIMEOUT"; then
  log "OK"
  exit 0
fi

# ── 9. 롤백 ──────────────────────────────────────────────────────────────
log "헬스체크 실패"

if [ -z "$prev_image" ]; then
  log "롤백 대상이 없다 (최초 배포)"
  exit 1
fi

log "롤백: $prev_image"
printf 'BACKEND_IMAGE=%s\n' "$prev_image" >"$APP_DIR/.env.image"
dc up -d

if wait_healthy "$HEALTH_TIMEOUT"; then
  log "ROLLED_BACK to $prev_image"
  exit 2
fi

# 두 번 연속 실패는 이미지 문제가 아니다. DB 가 죽었거나 디스크가 찼거나
# 설정이 깨진 것이다. 재시도로 해결되지 않는다.
# 컨테이너를 내리지 않는다 — 로그와 상태가 사라지면 원인을 못 찾는다.
log "ROLLBACK_FAILED"
exit 3
