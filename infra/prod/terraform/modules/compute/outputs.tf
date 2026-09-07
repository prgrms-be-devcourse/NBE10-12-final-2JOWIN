output "instance_id" {
  description = <<-EOT
    EC2 인스턴스 ID.
    cost-guard 의 stop_instance_ids 에 넣어 100% 정지 액션을 완성한다 —
    Budget Action 은 태그를 못 받고 ID 만 받기 때문이다.
  EOT
  value       = aws_instance.main.id
}

output "eip_public_ip" {
  description = "고정 공인 IP. dnszi 에 A 레코드로 등록한다 (수동)"
  value       = aws_eip.main.public_ip
}

output "private_ip" {
  description = "사설 IP"
  value       = aws_instance.main.private_ip
}

output "instance_role_name" {
  description = "인스턴스 역할 이름. 다른 모듈이 정책을 더 붙일 때 쓴다"
  value       = aws_iam_role.instance.name
}

output "instance_role_arn" {
  description = "인스턴스 역할 ARN"
  value       = aws_iam_role.instance.arn
}

output "availability_zone" {
  description = "인스턴스가 뜬 AZ. EBS 가 여기 묶인다"
  value       = aws_instance.main.availability_zone
}
