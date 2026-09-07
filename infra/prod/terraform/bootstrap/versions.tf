# bootstrap 스택 — 상태 저장소와 신원만 만든다.
# 이 스택은 사람이 로컬에서 1회 apply 하고, 이후 거의 건드리지 않는다.

terraform {
  # S3 네이티브 락(use_lockfile)은 1.10부터. DynamoDB 락 테이블을 안 쓰는 근거.
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
