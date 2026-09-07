# ─────────────────────────────────────────────────────────────────────────────
# 계층 ② 감지 — 알림 경로
#
#   Budgets ──> SNS ──> Lambda ──> Discord 웹훅
#
# Lambda 가 끼는 이유: SNS 는 구독자에게 밀어넣는 구조라 AWS 안에 받는 쪽이
# 있어야 한다. AWS Chatbot 은 Slack·Teams 만 지원하고 Discord 는 안 된다.
#
# 설계 근거: cost-guard.md §4.4
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_sns_topic" "cost_alert" {
  name = "${var.project}-cost-alert"

  # KMS 암호화를 걸지 않는다.
  # aws/sns 관리형 키를 쓰면 Budgets 가 이 토픽에 publish 할 때 kms 권한을
  # 받을 수 없어 알림 경로 자체가 끊긴다. 고객 관리형 키는 월 $1 — 예산의 1.4%.
  # 흐르는 내용은 비용 숫자뿐이라 개인정보가 없다.

  tags = {
    Component = "cost-guard"
    Layer     = "2-detect"
  }
}

# Budgets 서비스가 이 토픽에 publish 할 수 있게 연다.
# SourceAccount 조건으로 다른 계정의 Budgets 가 끼어드는 경로를 막는다.
data "aws_iam_policy_document" "cost_alert_topic" {
  statement {
    sid       = "AllowBudgetsPublish"
    effect    = "Allow"
    actions   = ["SNS:Publish"]
    resources = [aws_sns_topic.cost_alert.arn]

    principals {
      type        = "Service"
      identifiers = ["budgets.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_sns_topic_policy" "cost_alert" {
  arn    = aws_sns_topic.cost_alert.arn
  policy = data.aws_iam_policy_document.cost_alert_topic.json
}

# ── Lambda 번역기 ─────────────────────────────────────────────────────────

data "archive_file" "discord_relay" {
  type        = "zip"
  source_file = "${path.module}/lambda/discord_relay.py"
  output_path = "${path.module}/lambda/discord_relay.zip"
}

data "aws_iam_policy_document" "discord_relay_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "discord_relay" {
  name               = "${var.project}-discord-relay"
  description        = "Execution role for the SNS to Discord relay function."
  assume_role_policy = data.aws_iam_policy_document.discord_relay_trust.json
}

data "aws_iam_policy_document" "discord_relay" {
  statement {
    sid    = "WriteOwnLogs"
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.discord_relay.arn}:*"]
  }

  # 웹훅 URL 하나만 읽는다. 다른 파라미터는 못 본다.
  statement {
    sid       = "ReadWebhookParameter"
    effect    = "Allow"
    actions   = ["ssm:GetParameter"]
    resources = ["arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${var.discord_webhook_ssm_path}"]
  }

  # SecureString 을 복호화하려면 SSM 기본 키에 대한 Decrypt 가 필요하다.
  statement {
    sid       = "DecryptSecureString"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.aws_region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "discord_relay" {
  name   = "relay"
  role   = aws_iam_role.discord_relay.id
  policy = data.aws_iam_policy_document.discord_relay.json
}

# 로그 그룹을 먼저 만든다. Lambda 가 알아서 만들게 두면 보존 기간이
# "만료 없음"으로 잡혀 로그가 계속 쌓이고 요금이 붙는다.
resource "aws_cloudwatch_log_group" "discord_relay" {
  name              = "/aws/lambda/${var.project}-discord-relay"
  retention_in_days = var.log_retention_days

  tags = {
    Component = "cost-guard"
  }
}

resource "aws_lambda_function" "discord_relay" {
  function_name = "${var.project}-discord-relay"
  description   = "Relays AWS Budgets SNS notifications to a Discord webhook."
  role          = aws_iam_role.discord_relay.arn
  handler       = "discord_relay.handler"
  runtime       = "python3.12"
  timeout       = 15
  memory_size   = 128

  # 동시 실행을 묶어 둔다. 알림이 폭주해도 Lambda 가 무한히 늘어나
  # 비용을 만드는 경로를 막는다 — 비용 가드 모듈이 스스로 비용을 내면 안 된다.
  reserved_concurrent_executions = 2

  filename         = data.archive_file.discord_relay.output_path
  source_code_hash = data.archive_file.discord_relay.output_base64sha256

  environment {
    variables = {
      WEBHOOK_SSM_PATH = var.discord_webhook_ssm_path
    }
  }

  depends_on = [
    aws_iam_role_policy.discord_relay,
    aws_cloudwatch_log_group.discord_relay,
  ]

  tags = {
    Component = "cost-guard"
    Layer     = "2-detect"
  }
}

resource "aws_lambda_permission" "from_sns" {
  statement_id  = "AllowExecutionFromSNS"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.discord_relay.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.cost_alert.arn
}

resource "aws_sns_topic_subscription" "to_discord" {
  topic_arn = aws_sns_topic.cost_alert.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.discord_relay.arn

  depends_on = [aws_lambda_permission.from_sns]
}
