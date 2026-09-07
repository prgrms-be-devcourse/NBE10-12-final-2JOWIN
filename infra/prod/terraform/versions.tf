# prod 메인 스택 — CI(GitHub Actions)가 apply 한다.
# bootstrap 스택과 달리 사람이 로컬에서 돌리지 않는다.

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
  }
}
