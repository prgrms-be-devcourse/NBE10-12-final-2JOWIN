# 다른 스택·워크플로가 받아가는 값. 이 스택의 유일한 인터페이스다.

output "state_bucket_name" {
  description = "메인 스택 backend.tf 의 bucket 값"
  value       = aws_s3_bucket.tfstate.id
}

output "oidc_provider_arn" {
  description = "향후 역할을 추가할 때 신뢰 정책이 참조한다"
  value       = data.aws_iam_openid_connect_provider.github.arn
}

output "tf_plan_role_arn" {
  description = ".github/workflows 의 PR plan 잡"
  value       = aws_iam_role.tf_plan.arn
}

output "tf_apply_role_arn" {
  description = ".github/workflows 의 apply 잡"
  value       = aws_iam_role.tf_apply.arn
}

output "tf_apply_role_name" {
  description = "인프라 apply 역할 이름"
  value       = aws_iam_role.tf_apply.name
}

output "gha_deploy_role_arn" {
  description = ".github/workflows/deploy.yml"
  value       = aws_iam_role.gha_deploy.arn
}

output "account_id" {
  description = "apply 대상 계정. 의도한 계정이 맞는지 육안 확인용"
  value       = data.aws_caller_identity.current.account_id
}
