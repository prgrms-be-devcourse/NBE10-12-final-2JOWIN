#!/usr/bin/env bash
# 야간 PostgreSQL 덤프 -> S3 backup/
#
# 인스턴스 프로파일에는 backup/* 에 대한 PutObject 만 있다. 읽기도 삭제도 못 한다 —
# 서버가 침해돼도 과거 백업은 남는다(storage.md §4).
#
# 시크릿을 이 스크립트가 다루지 않는다. postgres 컨테이너 안에서 유닉스 소켓으로
# 붙으면 비밀번호가 필요 없고, 사용자·DB 이름은 그 컨테이너의 환경변수에 이미 있다.
#
# 설계 근거: containers.md §3.3 · storage.md §3.3
set -euo pipefail

BUCKET="${BACKUP_BUCKET:?BACKUP_BUCKET 이 필요하다}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-twojo}"

log() { echo "[backup] $*"; }

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

log "s3://${BUCKET}/${key} 업로드 완료"
