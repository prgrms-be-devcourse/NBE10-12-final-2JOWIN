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

# ── 네트워크 ───────────────────────────────────────────────────────

output "vpc_id" {
  description = "VPC ID"
  value       = module.network.vpc_id
}

output "primary_subnet_id" {
  description = "EC2 가 들어갈 서브넷"
  value       = module.network.primary_subnet_id
}

output "web_security_group_id" {
  description = "80·443 만 열린 보안그룹"
  value       = module.network.web_security_group_id
}

# ── 저장소 ─────────────────────────────────────────────────────────

output "ecr_repository_url" {
  description = "이미지 push·pull 주소. deploy 워크플로가 받는다"
  value       = module.storage.ecr_repository_url
}

output "config_bucket_name" {
  description = "설정·백업 버킷 이름"
  value       = module.storage.bucket_name
}

output "instance_access_policy_arn" {
  description = "compute(#91)의 인스턴스 프로파일에 붙일 정책 ARN"
  value       = module.storage.instance_access_policy_arn
}
