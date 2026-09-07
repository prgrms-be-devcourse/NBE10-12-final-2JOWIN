provider "aws" {
  region = var.aws_region

  # Cost Explorer 에서 태그별로 비용을 쪼개려면 이 태그가 필요하다.
  # Project 값은 cost-guard 의 ec2:ResourceTag/Project 조건과 같아야 한다.
  #
  # 주의: Billing 콘솔에서 "비용 할당 태그"를 수동 활성화해야 하고,
  #       데이터 반영에 24시간 걸린다 (cost-guard.md §3.4).
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
