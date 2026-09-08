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

# ── 컴퓨트 ─────────────────────────────────────────────────────────

output "instance_id" {
  description = "EC2 인스턴스 ID"
  value       = module.compute.instance_id
}

output "eip_public_ip" {
  description = "고정 공인 IP. dnszi 에 A 레코드로 등록한다"
  value       = module.compute.eip_public_ip
}

output "instance_role_name" {
  description = "인스턴스 역할 이름"
  value       = module.compute.instance_role_name
}
