#!/usr/bin/env bash
# 야간 PostgreSQL 덤프 -> S3 backup/
#
# 인스턴스 프로파일에는 backup/* 에 대한 PutObject 만 있다. 읽기도 삭제도 못 한다 —
# 서버가 침해돼도 과거 백업은 남는다(storage.md §4).
#
# 시크릿을 이 스크립트가 다루지 않는다. postgres 컨테이너 안에서 유닉스 소켓으로
# 붙으면 비밀번호가 필요 없고, 사용자·DB 이름은 그 컨테이너의 환경변수에 이미 있다.
#
# 종료 코드
#   0   성공
#   1   실패
#   75  배포가 진행 중이라 건너뛰었다 (장애가 아니다)
#
# 부르는 것: systemd user timer (infra/prod/systemd/backup.timer)
# 설계 근거: containers.md §3.3 · storage.md §3.3 · 이슈 #159
set -euo pipefail

# 인스턴스가 자기 자신에 대해 아는 것(버킷·리전). cloud-init 이 쓴다.
CONF=/etc/2jo.conf
# shellcheck source=/dev/null
[ -r "$CONF" ] && . "$CONF"

APP_DIR="${APP_DIR:-/opt/2jo}"
BUCKET="${BUCKET:?BUCKET 을 모른다 — /etc/2jo.conf 를 확인할 것}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-twojo}"

# node_exporter 가 textfile collector 로 읽어가는 자리.
# 이 파일이 갱신되지 않는 것 자체가 알람 조건이다 — 실패보다 부재가 위험하다.
METRICS_DIR="${METRICS_DIR:-$APP_DIR/metrics}"
METRICS_FILE="$METRICS_DIR/backup.prom"

log() { echo "[backup] $*"; }

# ── 배포와 겹치지 않게 ────────────────────────────────────────────────────
# deploy.sh 가 잡는 것과 같은 락이다. 배포 도중에 덤프를 뜨면 마이그레이션
# 중간 상태가 백업으로 남는다 — 그 백업은 복원해도 쓸 수 없다.
#
# 기다리다 포기하면 75 다. 실패로 두면 배포와 겹칠 때마다 알림이 울리는데,
# 하루 한 번 거른 것은 장애가 아니다. 이틀 연속 걸러지면 마지막 성공 시각이
# 25시간을 넘어 알람이 대신 잡는다.
exec 200>"$APP_DIR/.deploy.lock"
if ! flock -w 600 200; then
  log "배포가 진행 중이다 — 이번 회차를 건너뛴다"
  exit 75
fi

started="$(date -u +%s)"

# 컨테이너 이름 대신 compose 라벨로 찾는다. 이름 규칙이 바뀌어도 따라간다.
cid="$(docker ps -q \
  --filter "label=com.docker.compose.project=${COMPOSE_PROJECT}" \
  --filter "label=com.docker.compose.service=postgres")"

if [ -z "$cid" ]; then
  log "postgres 컨테이너를 찾지 못했다 — 백업을 건너뛴다"
  exit 1
fi

stamp="$(date -u +%Y%m%d-%H%M)"
key="backup/twojo-${stamp}.sql.gz"

# 파이프 중간이 실패해도 실패로 잡히게 pipefail 에 의존한다.
# 임시 파일을 만들지 않는다 — 디스크 여유가 넉넉하지 않다.
docker exec -i "$cid" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' |
  gzip -9 |
  aws s3 cp - "s3://${BUCKET}/${key}" --region "$AWS_REGION"

finished="$(date -u +%s)"
log "s3://${BUCKET}/${key} 업로드 완료 ($((finished - started))초)"

# ── 성공 시각 기록 ────────────────────────────────────────────────────────
# 실패 알림이 아니라 "마지막 성공이 언제냐"를 내보낸다. 이 방식만이
# 타이머가 아예 돌지 않은 경우까지 잡는다 — 실패는 흔적을 남기지만
# 부재는 흔적조차 남기지 않는다.
#
# 지표 이름이 twojo_ 인 이유: Prometheus 지표는 숫자로 시작할 수 없다.
install -d -m 755 "$METRICS_DIR"
tmp="$(mktemp "$METRICS_DIR/.backup.prom.XXXXXX")"
cat >"$tmp" <<METRICS
# HELP twojo_backup_last_success_timestamp_seconds 마지막으로 백업이 성공한 유닉스 시각
# TYPE twojo_backup_last_success_timestamp_seconds gauge
twojo_backup_last_success_timestamp_seconds ${finished}
# HELP twojo_backup_last_duration_seconds 마지막 백업에 걸린 시간
# TYPE twojo_backup_last_duration_seconds gauge
twojo_backup_last_duration_seconds $((finished - started))
METRICS
# node_exporter 가 반쯤 쓰인 파일을 읽지 않도록 원자적으로 바꾼다.
chmod 644 "$tmp"
mv "$tmp" "$METRICS_FILE"
