output "guardrail_policy_arn" {
  description = "비용 가드레일 정책 ARN. 나중에 추가되는 역할에도 같은 정책을 붙일 때 쓴다"
  value       = module.cost_guard.guardrail_policy_arn
}

output "guardrail_policy_json" {
  description = "생성된 Deny 정책 전문. plan 에서 실제 JSON 을 눈으로 확인할 때 쓴다"
  value       = module.cost_guard.guardrail_policy_json
}

output "cost_alert_topic_arn" {
  description = "비용 알림 SNS 토픽. 다른 모듈이 알림을 재사용할 때 쓴다"
  value       = module.cost_guard.sns_topic_arn
}

output "budget_name" {
  description = "생성된 예산 이름. 콘솔에서 찾을 때 쓴다"
  value       = module.cost_guard.budget_name
}
