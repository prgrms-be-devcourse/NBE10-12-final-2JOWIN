output "guardrail_policy_arn" {
  description = "가드레일 정책 ARN. 나중에 추가되는 역할에도 같은 정책을 부착할 때 쓴다"
  value       = aws_iam_policy.cost_guardrail.arn
}

output "guardrail_policy_json" {
  description = "생성된 Deny 정책 전문. plan 에서 실제 JSON 을 눈으로 확인할 때 쓴다"
  value       = data.aws_iam_policy_document.cost_guardrail.json
}

output "deny_create_policy_arn" {
  description = "90% 봉쇄 정책 ARN. 재개할 때 이 정책을 수동으로 detach 한다"
  value       = aws_iam_policy.deny_create.arn
}

output "sns_topic_arn" {
  description = "비용 알림 SNS 토픽. 배포 알림·Grafana 알람도 같은 채널을 쓴다"
  value       = aws_sns_topic.cost_alert.arn
}

output "budget_name" {
  description = "생성된 예산 이름"
  value       = aws_budgets_budget.monthly.name
}

output "discord_relay_function_name" {
  description = "SNS to Discord 번역 Lambda 이름. 로그를 볼 때 쓴다"
  value       = aws_lambda_function.discord_relay.function_name
}

output "budget_actions_enabled" {
  description = "Budget Action 2종이 실제로 만들어졌는지. Organizations 제약 시 false"
  value       = var.enable_budget_actions
}
