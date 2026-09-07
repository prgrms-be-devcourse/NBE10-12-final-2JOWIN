#!/usr/bin/env bash
# SSM Parameter Store -> /opt/2jo/.env
#
# 시크릿을 terraform 으로 만들지 않는 이유: tfstate 는 평문이고 tf-plan 역할이
# 그걸 읽을 수 있다. 사람이 SecureString 으로 넣어둔 값을 런타임에 꺼낸다.
#
# 파라미터 이름의 마지막 조각이 그대로 환경변수 키가 된다.
#   /2jo/prod/jwt-secret   ->  JWT_SECRET
#   /2jo/prod/db-password  ->  DB_PASSWORD
#
# BACKEND_IMAGE 는 여기서 쓰지 않는다. 이 스크립트가 .env 를 통째로 새로 쓰기
# 때문에, 배포마다 바뀌는 값은 .env.image 에 따로 둔다(deploy.sh 소유).
#
# 설계 근거: containers.md §7
set -euo pipefail

SSM_PREFIX="${SSM_PREFIX:-/2jo/prod}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
APP_DIR="${APP_DIR:-/opt/2jo}"
ENV_FILE="$APP_DIR/.env"

log() { echo "[fetch-secrets] $*"; }

# 임시 파일에 다 쓰고 마지막에 옮긴다. 중간에 실패해도 기존 .env 가 살아 있다.
tmp="$(mktemp "${ENV_FILE}.XXXXXX")"
# 만들자마자 권한을 좁힌다 — 잠깐이라도 넓게 열려 있으면 안 된다.
chmod 600 "$tmp"
trap 'rm -f "$tmp"' EXIT

{
  echo "# fetch-secrets.sh 가 생성한다. 직접 고치지 말 것 — 다음 실행에 덮어쓴다."
  echo "# 출처: SSM ${SSM_PREFIX}/*"
  echo "SPRING_PROFILES_ACTIVE=prod"
  echo "COOKIE_SECURE=true"
} >"$tmp"

count=0
while IFS=$'\t' read -r name value; do
  [ -n "$name" ] || continue
  key="$(basename "$name" | tr 'a-z-' 'A-Z_')"
  printf '%s=%s\n' "$key" "$value" >>"$tmp"
  count=$((count + 1))
done < <(
  aws ssm get-parameters-by-path \
    --path "$SSM_PREFIX" --recursive --with-decryption \
    --region "$AWS_REGION" \
    --query 'Parameters[].[Name,Value]' --output text
)

if [ "$count" -eq 0 ]; then
  log "경고: ${SSM_PREFIX} 아래에 파라미터가 하나도 없다"
fi

mv "$tmp" "$ENV_FILE"
chmod 600 "$ENV_FILE"
trap - EXIT

# 값은 절대 출력하지 않는다. 로그에 남으면 SSM 에 넣은 의미가 없다.
log "${count}개 키를 ${ENV_FILE} 에 썼다"
