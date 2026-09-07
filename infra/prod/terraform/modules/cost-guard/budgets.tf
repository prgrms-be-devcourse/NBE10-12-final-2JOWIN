# ─────────────────────────────────────────────────────────────────────────────
# 계층 ② 감지 + 계층 ③ 차단 — AWS Budgets
#
# 한계를 먼저 적어둔다: Budgets 는 달력 월 기준이라 매월 1일에 리셋된다.
# 이 프로젝트는 9~10월을 걸치므로 총액은 이걸로 못 지킨다.
#   - 월 내 폭주 감지  -> 여기 (Budgets)
#   - 총액 판정·차단   -> .github/workflows/cost-report.yml 일일 잡
#
# 설계 근거: cost-guard.md §1.4 · §4.1 · §5
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_budgets_budget" "monthly" {
  name         = "${var.project}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.budget_limit_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # 실측 경고 — 페이스 점검용. 차단은 하지 않는다.
  dynamic "notification" {
    for_each = toset(var.warn_thresholds)

    content {
      comparison_operator       = "GREATER_THAN"
      threshold                 = notification.value
      threshold_type            = "PERCENTAGE"
      notification_type         = "ACTUAL"
      subscriber_sns_topic_arns = [aws_sns_topic.cost_alert.arn]
    }
  }

  # 예측 초과 — 이게 가장 유용하다.
  # 실측 80% 는 이미 늦었고, 예측은 며칠 전에 온다.
  notification {
    comparison_operator       = "GREATER_THAN"
    threshold                 = 100
    threshold_type            = "PERCENTAGE"
    notification_type         = "FORECASTED"
    subscriber_sns_topic_arns = [aws_sns_topic.cost_alert.arn]
  }
}

# ── Budget Action 실행 역할 ───────────────────────────────────────────────
# Budgets 서비스가 이 역할을 맡아 정책을 붙이거나 EC2 를 멈춘다.

data "aws_iam_policy_document" "budget_action_trust" {
  count = var.enable_budget_actions ? 1 : 0

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["budgets.amazonaws.com"]
    }

    # 다른 계정의 Budgets 가 이 역할을 맡는 경로를 막는다.
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "budget_action" {
  count = var.enable_budget_actions ? 1 : 0

  name               = "${var.project}-budget-action"
  description        = "Assumed by AWS Budgets to attach the freeze policy and stop instances at threshold."
  assume_role_policy = data.aws_iam_policy_document.budget_action_trust[0].json
}

data "aws_iam_policy_document" "budget_action" {
  count = var.enable_budget_actions ? 1 : 0

  # 90% 봉쇄 — deny_create 정책을 terraform 실행 역할에 붙인다.
  # 붙일 수 있는 정책과 붙일 대상을 둘 다 좁힌다.
  statement {
    sid    = "AttachFreezePolicy"
    effect = "Allow"
    actions = [
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
    ]
    resources = ["arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/${var.terraform_exec_role_name}"]

    condition {
      test     = "ArnEquals"
      variable = "iam:PolicyARN"
      values   = [aws_iam_policy.deny_create.arn]
    }
  }

  statement {
    sid    = "ReadPolicyState"
    effect = "Allow"
    actions = [
      "iam:GetPolicy",
      "iam:GetRole",
      "iam:ListAttachedRolePolicies",
    ]
    resources = ["*"] # 읽기 전용. 대상을 좁히면 Budgets 의 사전 검사가 실패한다
  }

  # 100% 정지 — Budgets 는 SSM Automation 문서를 통해 인스턴스를 멈춘다.
  # 문장을 셋으로 나눈다. 태그 조건을 한 문장에 몰아넣으면 SSM 실행과 Describe
  # 쪽에는 그 태그가 없어 요청 전체가 거부된다.
  statement {
    sid    = "RunStopAutomation"
    effect = "Allow"
    actions = [
      "ssm:StartAutomationExecution",
      "ssm:GetAutomationExecution",
    ]
    resources = ["*"] # 실행 ID 는 실행 시점에 정해져 좁힐 수 없다
  }

  # 실제 정지는 이 프로젝트 인스턴스로만 제한한다.
  # 회사 계정이라 다른 팀 인스턴스가 같이 있을 수 있다.
  statement {
    sid       = "StopProjectInstancesOnly"
    effect    = "Allow"
    actions   = ["ec2:StopInstances"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/Project"
      values   = [var.project]
    }
  }

  statement {
    sid    = "DescribeForVerification"
    effect = "Allow"
    actions = [
      "ec2:DescribeInstances",
      "ec2:DescribeInstanceStatus",
    ]
    resources = ["*"] # Describe 계열은 리소스 수준 권한을 지원하지 않는다
  }
}

resource "aws_iam_role_policy" "budget_action" {
  count = var.enable_budget_actions ? 1 : 0

  name   = "budget-action"
  role   = aws_iam_role.budget_action[0].id
  policy = data.aws_iam_policy_document.budget_action[0].json
}

# ── 90% — 신규 생성 봉쇄 ──────────────────────────────────────────────────
# 서비스는 계속 돈다. 새로 만드는 것만 막힌다.
resource "aws_budgets_budget_action" "freeze_create" {
  count = var.enable_budget_actions ? 1 : 0

  budget_name        = aws_budgets_budget.monthly.name
  action_type        = "APPLY_IAM_POLICY"
  approval_model     = "AUTOMATIC"
  notification_type  = "ACTUAL"
  execution_role_arn = aws_iam_role.budget_action[0].arn

  action_threshold {
    action_threshold_type  = "PERCENTAGE"
    action_threshold_value = var.freeze_threshold
  }

  definition {
    iam_action_definition {
      policy_arn = aws_iam_policy.deny_create.arn
      roles      = [var.terraform_exec_role_name]
    }
  }

  subscriber {
    address           = aws_sns_topic.cost_alert.arn
    subscription_type = "SNS"
  }
}

# ── 100% — EC2 정지 ───────────────────────────────────────────────────────
# Budget Action 은 태그를 못 받고 인스턴스 ID 만 받는다.
# 그래서 compute 모듈이 만들어진 뒤에야 채울 수 있다 — 비어 있으면 안 만든다.
# 그 사이 구간은 일일 잡 백스톱이 덮는다 (cost-guard.md §5.3).
resource "aws_budgets_budget_action" "stop_ec2" {
  count = var.enable_budget_actions && length(var.stop_instance_ids) > 0 ? 1 : 0

  budget_name        = aws_budgets_budget.monthly.name
  action_type        = "RUN_SSM_DOCUMENTS"
  approval_model     = "AUTOMATIC"
  notification_type  = "ACTUAL"
  execution_role_arn = aws_iam_role.budget_action[0].arn

  action_threshold {
    action_threshold_type  = "PERCENTAGE"
    action_threshold_value = var.stop_threshold
  }

  definition {
    ssm_action_definition {
      action_sub_type = "STOP_EC2_INSTANCES"
      instance_ids    = var.stop_instance_ids
      region          = var.aws_region
    }
  }

  subscriber {
    address           = aws_sns_topic.cost_alert.arn
    subscription_type = "SNS"
  }
}

# ── Cost Anomaly Detection 은 넣지 않았다 ─────────────────────────────────
# 설계(§4.2)에도 "30일 프로젝트는 학습 기준선이 없어 초반 거의 무용"이라고
# 적혀 있다. 게다가 CE 이상 탐지 구독은 us-east-1 SNS 토픽만 받으므로,
# 알림을 Discord 로 흘리려면 us-east-1 에 SNS + Lambda 를 한 벌 더 만들어야 한다.
# 얻는 것 대비 붙는 리소스가 커서 뺐다. 필요해지면 별도 이슈로 추가한다.
