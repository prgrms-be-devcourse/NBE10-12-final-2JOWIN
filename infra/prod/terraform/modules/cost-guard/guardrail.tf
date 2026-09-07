# ─────────────────────────────────────────────────────────────────────────────
# 계층 ① 예방 — IAM Deny 가드레일
#
# 원리: 명시적 Deny 는 모든 Allow 를 이긴다. 역할에 AdministratorAccess 가 붙어
#       있어도 여기서 막힌다. 지연이 없고(요청 시점 거부) terraform state 도
#       오염되지 않는다 — 생성 API 자체가 실패하기 때문이다.
#
# 설계 근거: cost-guard.md §3.1
# ─────────────────────────────────────────────────────────────────────────────

data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

locals {
  # 자기 보호 Statement 가 참조할 ARN.
  # aws_iam_policy.cost_guardrail.arn 을 쓰면 리소스가 자기 자신을 참조해
  # 순환이 되므로, 결정적인 ARN 형식으로 직접 조립한다.
  guardrail_policy_arn = "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:policy/${var.guardrail_policy_name}"
}

data "aws_iam_policy_document" "cost_guardrail" {

  # ── 1. 고정비가 큰 서비스는 생성 자체를 막는다 ────────────────────────────
  # 이 프로젝트는 단일 EC2 + 컨테이너 PostgreSQL 구성이라 아래 서비스를
  # 하나도 쓰지 않는다. 실수로 들어오면 예산이 한 번에 무너진다.
  statement {
    sid    = "DenyExpensiveServices"
    effect = "Deny"

    actions = [
      "ec2:CreateNatGateway",         # 월 $32 — 단일 항목으로 예산의 56%
      "ec2:CreateTransitGateway",     # 월 $36
      "ec2:CreateVpcEndpoint",        # Interface 기준 개당 월 $7
      "elasticloadbalancing:Create*", # ALB/NLB/CLB — 월 $18
      "rds:CreateDBInstance",         # 월 $14
      "rds:CreateDBCluster",
      "eks:CreateCluster", # 월 $73
      "elasticache:CreateCacheCluster",
      "elasticache:CreateReplicationGroup",
      "es:CreateDomain",
      "opensearch:CreateDomain",
      "globalaccelerator:Create*",
      "redshift:CreateCluster",
      "sagemaker:CreateNotebookInstance",
      "sagemaker:CreateDomain",
    ]

    resources = ["*"]
  }

  # ── 2. 인스턴스 타입 허용 목록 ────────────────────────────────────────────
  # Resource 를 instance ARN 으로 잡아야 ec2:InstanceType 조건 키가 붙는다.
  # "*" 로 두면 RunInstances 가 함께 만드는 다른 리소스(볼륨·ENI)에도 조건이
  # 걸려 의도치 않게 전부 거부된다.
  statement {
    sid       = "DenyLargeInstanceTypes"
    effect    = "Deny"
    actions   = ["ec2:RunInstances"]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:*:*:instance/*"]

    condition {
      test     = "StringNotEquals"
      variable = "ec2:InstanceType"
      values   = var.allowed_instance_types
    }
  }

  # ── 3. EBS 볼륨 크기 상한 ─────────────────────────────────────────────────
  # Infracost 는 PR 에서만 잡는다. 콘솔에서 직접 키우는 경우까지 막으려면
  # 여기서 걸어야 한다.
  #
  # 검증 필요: ec2:ModifyVolume 이 ec2:VolumeSize 조건 키를 지원하는지.
  # 미지원이면 조건이 매칭되지 않아 Deny 가 발동하지 않는다(= 조용히 통과).
  # cost-guard.md §7.1 검증 체크리스트에서 실제로 확인할 것.
  statement {
    sid    = "DenyLargeVolumes"
    effect = "Deny"

    actions = [
      "ec2:CreateVolume",
      "ec2:ModifyVolume",
      "ec2:RunInstances", # 블록 디바이스 매핑으로 큰 볼륨을 다는 경로
    ]

    resources = ["arn:${data.aws_partition.current.partition}:ec2:*:*:volume/*"]

    condition {
      test     = "NumericGreaterThan"
      variable = "ec2:VolumeSize"
      values   = [tostring(var.max_volume_size_gb)]
    }
  }

  # ── 4. 리전 제한 ──────────────────────────────────────────────────────────
  # 다른 리전에 리소스를 만들어 놓고 잊는 사고를 막는다.
  #
  # NotAction 에 글로벌 서비스를 빼는 것이 핵심이다. IAM·STS·Budgets·Cost
  # Explorer 등은 us-east-1 로 보고되거나 aws:RequestedRegion 키가 없는데,
  # 이걸 빼지 않으면 terraform 자신과 비용 조회가 막혀 아무것도 못 한다.
  statement {
    sid    = "DenyOtherRegions"
    effect = "Deny"

    not_actions = [
      "iam:*",
      "sts:*",
      "budgets:*",
      "ce:*",
      "cur:*",
      "support:*",
      "organizations:*",
      "route53:*",
      "cloudfront:*",
      "waf:*",
    ]

    resources = ["*"]

    condition {
      test     = "StringNotEquals"
      variable = "aws:RequestedRegion"
      values   = var.allowed_regions
    }
  }

  # ── 5. 자기 보호 ──────────────────────────────────────────────────────────
  # 이 정책을 스스로 떼어낼 수 있으면 가드레일이 아니다.
  # terraform 이 "가드레일이 방해되네" 하고 detach 하는 경로를 차단한다.
  #
  # 90% 봉쇄 정책(deny_create)은 여기 포함하지 않는다 — 재개 절차(§5.4)에서
  # 사람이 수동으로 detach 해야 하기 때문이다.
  statement {
    sid    = "ProtectGuardrailPolicy"
    effect = "Deny"

    actions = [
      "iam:DeletePolicy",
      "iam:DeletePolicyVersion",
      "iam:CreatePolicyVersion",
      "iam:SetDefaultPolicyVersion",
      "iam:DetachRolePolicy",
      "iam:DetachUserPolicy",
    ]

    resources = [local.guardrail_policy_arn]
  }
}

resource "aws_iam_policy" "cost_guardrail" {
  name        = var.guardrail_policy_name
  path        = "/"
  description = "2JO cost guardrail: blocks expensive services, oversized instances and volumes, and other regions."
  policy      = data.aws_iam_policy_document.cost_guardrail.json

  tags = {
    Component = "cost-guard"
    Layer     = "1-prevention"
  }
}

# ── 부착 ──────────────────────────────────────────────────────────────────
# 역할과 사용자 양쪽에 건다. 역할만 걸면 콘솔에서 사람이 직접 만드는 경로가
# 열린 채로 남는다.

resource "aws_iam_role_policy_attachment" "terraform_exec" {
  role       = var.terraform_exec_role_name
  policy_arn = aws_iam_policy.cost_guardrail.arn
}

resource "aws_iam_user_policy_attachment" "developers" {
  for_each = toset(var.developer_user_names)

  user       = each.value
  policy_arn = aws_iam_policy.cost_guardrail.arn
}
