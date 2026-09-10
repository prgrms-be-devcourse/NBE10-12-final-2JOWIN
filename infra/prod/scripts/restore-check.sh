#!/usr/bin/env bash
# 백업 복원 리허설 — 덤프가 실제로 복원되는지, 몇 초 걸리는지 잰다
#
# 매일 백업이 S3 로 올라가지만 한 번도 복원해본 적이 없었다. 복원은 평소에
# 안 하는 작업이라, 그대로 두면 처음 하는 날이 사고 난 날이 된다 (#292).
#
# 운영 DB 는 읽지도 쓰지도 않는다. 덤프를 임시 컨테이너에 부어 확인하고
# 컨테이너째 버린다 — 리허설이 사고를 내면 안 된다.
#
# 임시 컨테이너는 운영과 같은 이미지를 쓴다. 버전이 어긋나는 것도 잡아야
# 할 결함이기 때문이다.
#
# 덤프를 어디서 가져오나
#   인자 없음      S3 backup/ 에서 가장 최근 것
#   -              표준입력 (gzip 스트림)
#   s3://... | 경로  지정한 것
#
# 인스턴스 프로파일에는 backup/* 에 대한 PutObject 만 있고 GetObject 는
# 없다 — 서버가 침해돼도 과거 백업은 남게 하려는 설계다(storage.md §4).
# 그래서 S3 에서 직접 받는 경로는 권한이 열려 있어야 동작한다. 열려 있지
# 않으면 무엇이 없는지 알려주고 멈춘다 — 조용히 다른 길로 새지 않는다.
#
# 종료 코드
#   0   복원·검증 성공
#   1   실패
#   75  배포가 진행 중이라 건너뛰었다 (장애가 아니다)
#
# 설계 근거: 이슈 #292 · #159
set -euo pipefail

CONF=/etc/2jo.conf
# shellcheck source=/dev/null
[ -r "$CONF" ] && . "$CONF"

APP_DIR="${APP_DIR:-/opt/2jo}"
BUCKET="${BUCKET:-}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-twojo}"

METRICS_DIR="${METRICS_DIR:-$APP_DIR/metrics}"
METRICS_FILE="$METRICS_DIR/restore-check.prom"

# 이름을 고정하면 앞 회차가 죽으면서 남긴 컨테이너와 부딪힌다.
CONTAINER="twojo-restore-check-$$"
PGPASS="rehearsal-$$"
RESTORE_DB="restorecheck"

SOURCE="${1:-}"

log() { echo "[restore-check] $*"; }

# ── 뒷정리 ────────────────────────────────────────────────────────────────
# 실패해도 반드시 지운다. 임시 컨테이너가 남으면 다음 회차가 포트·메모리를
# 놓고 다투고, 그건 리허설이 만든 사고다.
cleanup() {
  local rc=$?
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  [ -n "${WORK:-}" ] && rm -rf "$WORK"
  exit "$rc"
}
trap cleanup EXIT

# ── 배포와 겹치지 않게 ────────────────────────────────────────────────────
# backup.sh 와 같은 락이다. 부팅 직후(09시)에 밀린 야간 백업과 아침 첫
# 배포가 함께 오는데, 거기에 리허설까지 겹치면 서버가 셋을 동시에 돌린다.
exec 200>"$APP_DIR/.deploy.lock"
if ! flock -w 300 200; then
  log "배포가 진행 중이다 — 이번 회차를 건너뛴다"
  exit 75
fi

# ── 1. 덤프 고르기 ────────────────────────────────────────────────────────
if [ -z "$SOURCE" ]; then
  [ -n "$BUCKET" ] || { log "BUCKET 을 모른다 — /etc/2jo.conf 를 확인할 것"; exit 1; }

  log "S3 에서 가장 최근 덤프를 찾는다: s3://${BUCKET}/backup/"
  if ! listing="$(aws s3api list-objects-v2 \
    --bucket "$BUCKET" --prefix "backup/" \
    --query 'sort_by(Contents,&LastModified)[-1].Key' \
    --output text --region "$AWS_REGION" 2>&1)"; then
    log "목록을 읽지 못했다: ${listing}"
    log "인스턴스 프로파일에 s3:ListBucket 이 필요하다"
    exit 1
  fi

  # `A && B || C` 로 쓰지 않는다. 그 관용구는 if-then-else 가 아니고,
  # 그걸 그렇게 읽었다가 설정 전용 배포가 통째로 깨진 적이 있다 (#230).
  if [ -z "$listing" ] || [ "$listing" = "None" ]; then
    log "backup/ 이 비어 있다"
    exit 1
  fi
  SOURCE="s3://${BUCKET}/${listing}"
fi

log "대상: ${SOURCE}"

# 스트리밍하지 않고 파일로 받는다. 덤프에서 소유자 역할을 미리 읽어야 하는데
# 스트림은 두 번 읽을 수 없다. 덤프가 수십 KB 라 디스크 비용이 없다.
WORK="$(mktemp -d)"
DUMP="$WORK/dump.sql.gz"

if [ "$SOURCE" = "-" ]; then
  cat > "$DUMP"
elif [ "${SOURCE#s3://}" != "$SOURCE" ]; then
  aws s3 cp "$SOURCE" "$DUMP" --region "$AWS_REGION" >/dev/null
else
  cp "$SOURCE" "$DUMP"
fi

# ── 2. 임시 postgres ──────────────────────────────────────────────────────
# 운영과 같은 이미지. 볼륨도 공개 포트도 앱 네트워크도 없다.
pg_cid="$(docker ps -q \
  --filter "label=com.docker.compose.project=${COMPOSE_PROJECT}" \
  --filter "label=com.docker.compose.service=postgres" 2>/dev/null || true)"

image=""
db_user=""
if [ -n "$pg_cid" ]; then
  image="$(docker inspect --format '{{.Config.Image}}' "$pg_cid" 2>/dev/null || true)"
  db_user="$(docker exec "$pg_cid" printenv POSTGRES_USER 2>/dev/null || true)"
fi

# pg_dump 를 옵션 없이 떴기 때문에 덤프 안에 `OWNER TO <역할>` 이 들어 있다.
# 그 역할이 없는 데이터베이스에 부으면 첫 줄부터 막힌다 — 실제로 막혔다.
#
# 운영 컨테이너에서 사용자 이름을 읽는 것이 1순위지만, 정작 복원이 필요한
# 상황은 운영이 죽어 있는 때다. 그래서 덤프 자체에서도 읽어낸다.
if [ -z "$db_user" ]; then
  db_user="$(gunzip -c "$DUMP" | grep -m1 -oE 'OWNER TO [A-Za-z0-9_]+' | awk '{print $3}' || true)"
fi
db_user="${db_user:-postgres}"
image="${image:-postgres:16-alpine}"

log "임시 컨테이너 기동: ${image} (역할 ${db_user})"

docker run -d --name "$CONTAINER" \
  -e POSTGRES_PASSWORD="$PGPASS" \
  -e POSTGRES_USER="$db_user" \
  -e POSTGRES_DB="$RESTORE_DB" \
  "$image" >/dev/null

# 준비될 때까지 기다린다. 바로 부으면 "연결 거부" 가 복원 실패로 보인다.
ready=0
for _ in $(seq 1 30); do
  if docker exec "$CONTAINER" pg_isready -U "$db_user" -d "$RESTORE_DB" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
[ "$ready" = "1" ] || { log "임시 postgres 가 30초 안에 준비되지 않았다"; exit 1; }

# ── 3. 복원 ───────────────────────────────────────────────────────────────
# pg_dump 를 옵션 없이 떴기 때문에 --clean 도 --create 도 없다. 비어 있지
# 않은 DB 에 부으면 "이미 있다" 가 쏟아진다 — 그래서 새 DB 여야 한다.
#
# psql 은 기본적으로 오류가 나도 계속 진행하고 0 으로 끝난다. 그러면 절반만
# 복원된 것을 성공으로 읽는다. ON_ERROR_STOP=1 이 그걸 막는다.
started="$(date -u +%s)"

if ! gunzip -c "$DUMP" |
  docker exec -i -e PGPASSWORD="$PGPASS" "$CONTAINER" \
    psql -v ON_ERROR_STOP=1 -U "$db_user" -d "$RESTORE_DB" >/dev/null; then
  log "::복원에 실패했다"
  exit 1
fi

finished="$(date -u +%s)"
elapsed=$((finished - started))
log "복원 완료 (${elapsed}초)"

# ── 4. 검증 ───────────────────────────────────────────────────────────────
# "명령이 오류 없이 끝났다" 로는 부족하다. 빈 DB 가 만들어져도 오류는 안 난다.
q() { docker exec -e PGPASSWORD="$PGPASS" "$CONTAINER" \
  psql -U "$db_user" -d "$RESTORE_DB" -tAc "$1" 2>/dev/null || echo ""; }

tables="$(q "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")"
flyway="$(q "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"
rows="$(q "SELECT coalesce(sum(n_live_tup),0) FROM pg_stat_user_tables")"

log "테이블 ${tables:-0}개 · flyway ${flyway:-없음} · 행 ${rows:-0}개"

fail=0
[ "${tables:-0}" -gt 0 ] 2>/dev/null || { log "::error::테이블이 하나도 없다"; fail=1; }
[ -n "${flyway:-}" ] || { log "::error::flyway_schema_history 를 읽지 못했다"; fail=1; }
[ "${rows:-0}" -gt 0 ] 2>/dev/null || { log "::error::행이 하나도 없다"; fail=1; }
[ "$fail" = "0" ] || exit 1

# ── 5. 지표 ───────────────────────────────────────────────────────────────
# 이게 없으면 리허설도 "돌았는지 아무도 모르는" 것이 된다. 백업이 그랬다.
install -d -m 755 "$METRICS_DIR"
tmp="$(mktemp "$METRICS_DIR/.restore-check.prom.XXXXXX")"
cat >"$tmp" <<METRICS
# HELP twojo_restore_check_last_success_timestamp_seconds 마지막 복원 검증 성공 시각
# TYPE twojo_restore_check_last_success_timestamp_seconds gauge
twojo_restore_check_last_success_timestamp_seconds ${finished}
# HELP twojo_restore_check_duration_seconds 복원에 걸린 시간
# TYPE twojo_restore_check_duration_seconds gauge
twojo_restore_check_duration_seconds ${elapsed}
# HELP twojo_restore_check_rows 복원된 행 수
# TYPE twojo_restore_check_rows gauge
twojo_restore_check_rows ${rows}
METRICS
chmod 644 "$tmp"
mv "$tmp" "$METRICS_FILE"

log "OK"
