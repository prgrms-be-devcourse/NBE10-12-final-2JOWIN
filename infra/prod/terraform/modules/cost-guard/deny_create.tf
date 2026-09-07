# ─────────────────────────────────────────────────────────────────────────────
# 계층 ③ 차단 (1단계) — 90% 도달 시 부착될 봉쇄 정책
#
# 이 정책은 만들어만 두고 아무 데도 붙이지 않는다.
# Budget Action 이 90% 에서 terraform 실행 역할에 자동으로 붙인다.
#
# 목적: "새로 만드는 것"만 막고 "돌고 있는 것"은 건드리지 않는다.
#       한 번에 멈추면 시연 중 사이트가 죽는다. 90~100% 구간이 대응 시간이다.
#
# 설계 근거: cost-guard.md §5.1
# ─────────────────────────────────────────────────────────────────────────────

data "aws_iam_policy_document" "deny_create" {

  # 생성·기동 계열만 막는다. Describe·Get·List 는 열어 둔다 —
  # 원인을 조사하려면 읽기는 되어야 한다.
  statement {
    sid    = "DenyResourceCreation"
    effect = "Deny"

    actions = [
      "ec2:RunInstances",
      "ec2:StartInstances",
      "ec2:CreateVolume",
      "ec2:ModifyVolume",
      "ec2:AllocateAddress",
      "ec2:CreateNatGateway",
      "ec2:CreateVpcEndpoint",
      "ec2:CreateSnapshot",
      "ec2:CreateImage",
      "rds:Create*",
      "elasticloadbalancing:Create*",
      "eks:Create*",
      "elasticache:Create*",
      "es:Create*",
      "opensearch:Create*",
      "lambda:CreateFunction",
      "ecs:CreateCluster",
      "ecs:RunTask",
      "s3:CreateBucket",
      "ecr:CreateRepository",
    ]

    resources = ["*"]
  }
}

resource "aws_iam_policy" "deny_create" {
  name        = "${var.project}-deny-create"
  path        = "/"
  description = "Attached automatically by AWS Budgets at the freeze threshold. Blocks creating or starting resources; running workloads keep serving. Detach manually to resume."
  policy      = data.aws_iam_policy_document.deny_create.json

  tags = {
    Component = "cost-guard"
    Layer     = "3-block"
  }
}
