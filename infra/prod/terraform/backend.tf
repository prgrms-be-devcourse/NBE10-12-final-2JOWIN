# 상태는 bootstrap 이 만든 버킷에 둔다.
#
# bucket 을 코드에 박지 않는 이유: 버킷 이름에 계정 ID 가 들어간다.
# init 할 때 주입한다.
#
#   terraform init -backend-config="bucket=2jo-tfstate-<계정ID>"
#
# CI 는 같은 값을 시크릿에서 넣는다.

terraform {
  backend "s3" {
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true # DynamoDB 락 테이블 대신 S3 네이티브 락
  }
}
