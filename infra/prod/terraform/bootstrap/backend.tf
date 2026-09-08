# bootstrap 자신의 상태도 자기가 만든 버킷에 둔다.
#
# 순서가 이상해 보이지만 의도다. 처음 apply 할 때는 이 파일이 없는 상태로
# 로컬에 상태를 만들고, 버킷이 생긴 뒤에 이 파일을 두고 이관한다.
# README 의 실행 순서 3~5 단계가 그것이다.
#
# bucket 을 여기 박지 않는 이유는 메인 스택과 같다 — 이름에 계정 ID 가
# 들어간다. init 할 때 주입한다:
#
#   terraform init -migrate-state \
#     -backend-config="bucket=2jo-tfstate-<계정ID>"
#
# 이 스택은 CI 가 돌리지 않는다. 사람이 로컬에서 1회만 실행한다.

terraform {
  backend "s3" {
    key          = "bootstrap/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true # DynamoDB 락 테이블 대신 S3 네이티브 락
  }
}
