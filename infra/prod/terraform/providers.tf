provider "aws" {
  region = var.aws_region

  # 계정을 여러 팀이 공유한다. Project 태그로 우리 리소스를 식별하고
  # Cost Explorer 에서 팀별 비용을 분해한다.
  default_tags {
    tags = {
      Project   = var.project
      Env       = "prod"
      Owner     = "infra"
      ManagedBy = "terraform"
    }
  }
}

# us-east-1 별칭 프로바이더는 두지 않는다.
# Cost Anomaly Detection 을 뺐고(budgets.tf 하단 참고) 나머지 리소스는 전부
# ap-northeast-2 에 만든다. 쓰지 않는 프로바이더를 선언해 두면 tflint 가
# 죽은 코드로 잡는다. 필요해질 때 그 리소스와 함께 추가한다.
