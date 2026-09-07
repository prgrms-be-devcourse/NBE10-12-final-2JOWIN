provider "aws" {
  region = var.aws_region

  # 이 스택이 만드는 모든 리소스에 붙는다.
  # Project 태그는 cost-guard 의 ec2:ResourceTag/Project 조건과 같은 값이어야 한다.
  default_tags {
    tags = {
      Project   = var.project
      Stack     = "bootstrap"
      ManagedBy = "terraform"
    }
  }
}

# 계정 ID를 변수로 받지 않는다 — 실행 중인 자격증명에서 끌어온다.
# 잘못된 계정에 apply 하는 사고를 막고, tfvars 에 계정 ID를 적을 이유도 없앤다.
data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}
