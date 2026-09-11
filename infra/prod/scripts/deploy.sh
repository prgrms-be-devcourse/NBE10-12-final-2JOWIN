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
#   1   실패 · 롤백 대상 없음(최초 배포) 또는 배포 전 덤프 실패
#   2   실패 · 롤백 성공 (서비스는 정상)
#   3   실패 · 롤백도 실패 (즉시 확인 필요)
#   75  다른 배포가 진행 중
#
# 설계 근거: cd.md §6 · §7
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
# 스크레이프 주기가 30s 다. 성공한 스크레이프가 한 번은 지나가야 판정할 수
# 있어 그보다 넉넉히 잡는다. 전부 정상이면 대개 첫 질의에서 빠져나온다.
TARGET_TIMEOUT="${TARGET_TIMEOUT:-60}"

TARGET_IMAGE="${1:-}"

log() { echo "[deploy] $*"; }

# ── 1. 동시 실행 차단 ─────────────────────────────────────────────────────
# Actions 의 concurrency 는 워크플로만 막는다. 사람이 SSH 로 직접 부르는
# 경로는 여기서 막힌다.
#
# 락을 APP_DIR 안에 둔다. /var/lock 은 root 소유라 sudo 가 필요한데
# 이 스크립트는 ec2-user 로 돈다. 아래 s3 sync --delete 에서 반드시
# 제외해야 한다 — 지워지면 fd 는 살아 있는 채로 파일만 사라져서
# 다음 배포가 새 inode 에 락을 걸고 둘이 동시에 돈다.
exec 200>"$APP_DIR/.deploy.lock"
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
# --delete 는 S3 에 없는 파일을 지운다. 서버가 런타임에 만드는 것은 전부
# 여기서 빼야 한다. 빼지 않으면 방금 받은 시크릿과 이미지 태그가 지워진다.
#
# metrics/ 도 그런 자리다. backup.sh 가 마지막 성공 시각을 여기 떨구는데,
# 번들에는 없으니 배포마다 지워졌다. 바로 아래 install -d 가 빈 디렉터리를
# 다시 만들어서 흔적조차 남지 않았고, 그동안 "백업이 하루 넘게 없다"가
# critical 로 계속 울렸다 — 백업은 멀쩡한데 (#222).
log "설정 번들 동기화"
aws s3 sync "s3://${BUCKET}/config/" "$APP_DIR/" \
  --region "$AWS_REGION" --delete \
  --exclude ".env" --exclude ".env.image" --exclude ".deploy.lock" \
  --exclude "metrics/*"

# S3 는 실행 비트를 보존하지 않는다. 방금 받아온 스크립트는 전부 644 다.
chmod +x "$APP_DIR"/scripts/*.sh

# node_exporter 가 바인드 마운트하는 자리. compose 보다 먼저 있어야 한다 —
# 없으면 도커가 root 소유로 만들고, ec2-user 로 도는 backup.sh 가 못 쓴다.
install -d -m 755 "$APP_DIR/metrics"

# ── 3-1. 예약 작업 ───────────────────────────────────────────────────────
# 백업 타이머를 여기서 설치한다. cloud-init 이 아니라 배포가 하는 이유:
# user_data 를 고쳐도 인스턴스는 재생성되지도 재실행되지도 않는다
# (user_data_replace_on_change 기본 false). cloud-init 에 두면 스케줄을
# 한 번 정한 뒤로는 손으로 고치기 전까지 바뀌지 않는다.
#
# 실패해도 배포를 멈추지 않는다. 앱이 정상인데 타이머 하나 때문에 배포가
# 막히는 건 과하다. 대신 조용히 넘어가지도 않는다 — 타이머가 안 붙으면
# 백업 지표가 갱신되지 않고, 25시간 뒤 알람이 대신 잡는다 (이슈 #159).
install_timers() {
  # 비대화형 SSH 세션에는 이 변수가 없을 수 있다. linger 가 켜져 있으면
  # /run/user/<uid> 는 로그인과 무관하게 항상 존재한다.
  export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"

  local unit_dir="$HOME/.config/systemd/user"
  install -d "$unit_dir"
  cp "$APP_DIR"/systemd/*.service "$APP_DIR"/systemd/*.timer "$unit_dir/"

  systemctl --user daemon-reload
  systemctl --user enable --now backup.timer
}

if [ -d "$APP_DIR/systemd" ]; then
  if install_timers; then
    log "백업 타이머 설치 완료"
  else
    log "경고: 백업 타이머 설치 실패 — 배포는 계속한다. journalctl --user 확인할 것"
  fi
fi

# ── 4. 시크릿 확인 ───────────────────────────────────────────────────────
# 이 스크립트는 시크릿을 만들지도 읽지도 않는다. .env 는 배포 워크플로가
# GitHub Secrets 에서 조립해 SSH stdin 으로 미리 넣어둔다.
#
# 없으면 여기서 멈춘다. 그대로 진행하면 docker compose 가 --env-file 을
# 못 찾아 죽는데, 그 에러 메시지는 원인을 가리키지 않는다.
if [ ! -r "$APP_DIR/.env" ]; then
  log "$APP_DIR/.env 가 없다 — 배포 워크플로가 먼저 넣어야 한다"
  exit 1
fi

# ── 5. 배포 전 덤프 ──────────────────────────────────────────────────────
# 이미지를 바꿀 때만 뜬다. 설정 전용 배포는 스키마를 건드리지 않는다.
#
# 롤백은 이미지만 되돌린다. Flyway 가 이미 마이그레이션을 적용했다면
# 스키마는 새 상태로 남고, 옛 코드가 그 위에서 돌지 못할 수 있다 —
# 컬럼을 지우는 마이그레이션이 그렇다. 그때 기댈 곳이 이 덤프다.
#
# 야간 백업만으로는 부족하다. 하루 한 번이라 오후에 사고가 나면 그날
# 데이터를 잃는다.
#
# 실패하면 배포를 멈춘다. 복구 지점 없이 마이그레이션을 태우지 않는다 —
# 그게 이 단계가 존재하는 이유다.
if [ -n "$TARGET_IMAGE" ]; then
  pg_cid="$(container_of postgres || true)"
  if [ -z "$pg_cid" ]; then
    log "postgres 가 없다 — 최초 배포로 보고 덤프를 건너뛴다"
  else
    stamp="$(date -u +%Y%m%d-%H%M%S)"
    key="backup/pre-deploy/twojo-${stamp}.sql.gz"
    log "배포 전 덤프: s3://${BUCKET}/${key}"
    docker exec -i "$pg_cid" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' |
      gzip -9 |
      aws s3 cp - "s3://${BUCKET}/${key}" --region "$AWS_REGION"
    log "덤프 완료"
  fi
fi

# ── 6. 이미지 ─
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

# ── 7. 교체 ──────────────────────────────────────────────────────────────
log "컨테이너 기동"
dc up -d

# ── 8. 설정 반영 ─────────────────────────────────────────────────────────
# 바인드 마운트한 설정 파일이 바뀌어도 컨테이너는 재시작되지 않는다.
# up -d 만으로는 Caddyfile·prometheus.yml 변경이 반영되지 않는다.
# 컨테이너가 아직 없을 수 있으므로 실패는 무시한다.
dc exec -T caddy caddy reload --config /etc/caddy/Caddyfile 2>/dev/null ||
  log "caddy reload 건너뜀"
dc exec -T prometheus kill -HUP 1 2>/dev/null ||
  log "prometheus reload 건너뜀"

# ── 9. 판정 ──────────────────────────────────────────────────────────────
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

# ── 9-1. 스크레이프 타깃 확인 ────────────────────────────────────────────
# caddy 와 backend 타깃이 배포 첫날부터 죽어 있었는데 19시간 동안 아무도
# 몰랐다. 알람은 정상적으로 울리고 있었고 Discord 로도 갔다 — 우리가 안
# 봤다. 그래서 알람을 더 만드는 대신 배포가 스스로 확인하게 한다 (#222).
#
# 배포를 실패시키지 않는다. 앱은 멀쩡한데 모니터링 결함 때문에 롤백이
# 도는 것이 훨씬 나쁘다. 여기서는 로그에 남기는 것까지만 한다.
check_targets() {
  local cid ip body down waited=0

  cid="$(container_of prometheus || true)"
  [ -n "$cid" ] || { log "타깃 확인 건너뜀 (prometheus 없음)"; return 0; }
  ip="$(docker inspect \
    -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$cid" 2>/dev/null || true)"
  [ -n "$ip" ] || { log "타깃 확인 건너뜀 (IP 조회 실패)"; return 0; }

  # 스크레이프 주기가 30s 라, 교체 직후에는 재기동 중에 실패한 결과가 아직
  # 남아 있다. 성공한 스크레이프가 한 번은 지나가야 판정할 수 있다.
  while :; do
    body="$(curl -fsS --max-time 10 \
      "http://${ip}:9090/api/v1/query?query=up==0" 2>/dev/null || true)"
    case "$body" in
      *'"result":[]'*) log "스크레이프 타깃 전부 정상"; return 0 ;;
    esac
    [ "$waited" -lt "$TARGET_TIMEOUT" ] || break
    sleep 10
    waited=$((waited + 10))
  done

  [ -n "$body" ] || { log "타깃 확인 건너뜀 (질의 실패)"; return 0; }

  # pipefail 이라 grep 이 못 찾으면 파이프라인 전체가 실패한다 — 여기서 죽으면
  # 앱이 멀쩡한데 배포가 실패로 끝난다. 이름을 못 뽑는 것은 경고감이 아니다.
  down="$(printf '%s' "$body" | grep -o '"job":"[^"]*"' | cut -d'"' -f4 | sort -u | tr '\n' ' ' || true)"
  log "경고: 스크레이프 타깃이 응답하지 않는다 — ${down:-알 수 없음}"
}

# ── 9-2. 실패했을 때 증거 남기기 ─────────────────────────────────────────
# 롤백은 방금 뜬 컨테이너를 지운다. 그 전에 남기지 않으면 무엇 때문에
# 실패했는지 알 방법이 사라진다 — 배포자는 SSH 로 들어가지 못하고(22번은
# 배포 중에만 열린다), 들어간들 그 컨테이너는 이미 없다.
#
# 실제로 그랬다. 메일 어댑터 배포가 롤백됐는데 CI 로그에 남은 것은
# "헬스체크 실패" 한 줄뿐이었고, 원인을 코드를 읽어 추측해야 했다 (#333).
#
# 세 가지를 본다. 이 조합이면 "앱이 안 떴다"와 "앱은 떴는데 health 가
# DOWN 이다"가 구분된다 — 증상은 같지만 대응이 전혀 다르다.
dump_failure() {
  local cid
  cid="$(container_of backend || true)"
  if [ -z "$cid" ]; then
    log "진단 건너뜀 — backend 컨테이너가 없다 (기동 자체가 실패했다는 뜻이다)"
    return 0
  fi

  log "── 헬스체크 기록 (마지막 회차들) ──"
  docker inspect -f \
    '{{if .State.Health}}{{range .State.Health.Log}}[rc={{.ExitCode}}] {{.Output}}
{{end}}{{end}}' "$cid" 2>/dev/null | tail -5 || true

  # 컨테이너 헬스체크는 curl -f 라 503 이면 본문을 버린다. 지금 알고 싶은
  # 것이 그 본문이므로 -f 없이 한 번 더 부른다.
  log "── /actuator/health 본문 ──"
  docker exec "$cid" curl -s --max-time 5 http://localhost:8080/actuator/health 2>/dev/null ||
    log "(응답 없음 — 앱이 포트를 열지 못했다)"
  echo

  # 여기에 원인이 있다. 기동 실패면 스택트레이스가, health 만 DOWN 이면
  # 해당 인디케이터의 경고가 찍힌다(Spring 이 예외와 함께 남긴다).
  log "── 컨테이너 로그 (마지막 80줄) ──"
  docker logs --tail 80 "$cid" 2>&1 || true
}

if wait_healthy "$HEALTH_TIMEOUT"; then
  check_targets
  log "OK"
  exit 0
fi

# ── 10. 롤백 ──────────────────────────────────────────────────────────────
log "헬스체크 실패"
dump_failure

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
#
# 그래도 여기서 한 번 더 남긴다. 이 상태는 사람이 즉시 들어가야 하는데,
# 22번은 닫혀 있고 여는 데 시간이 걸린다. 그 사이에 볼 것이 있어야 한다.
dump_failure
log "ROLLBACK_FAILED"
exit 3
