output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "vpc_cidr" {
  description = "VPC CIDR. 보안그룹 규칙에서 내부 대역을 참조할 때 쓴다"
  value       = aws_vpc.main.cidr_block
}

output "public_subnet_ids" {
  description = "퍼블릭 서브넷 ID 맵 (AZ 접미사 -> ID)"
  value       = { for k, s in aws_subnet.public : k => s.id }
}

output "primary_subnet_id" {
  description = "EC2 를 둘 서브넷. compute 모듈이 받는다"
  value       = aws_subnet.public[var.primary_az_suffix].id
}

output "web_security_group_id" {
  description = "80·443 만 열린 보안그룹. compute 모듈이 받는다"
  value       = aws_security_group.web.id
}
