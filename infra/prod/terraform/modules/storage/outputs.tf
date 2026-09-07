output "ecr_repository_url" {
  description = "이미지 push·pull 주소. deploy 워크플로와 compute user_data 가 받는다"
  value       = aws_ecr_repository.backend.repository_url
}

output "ecr_repository_arn" {
  description = "ECR 저장소 ARN"
  value       = aws_ecr_repository.backend.arn
}

output "ecr_repository_name" {
  description = "저장소 이름. 배포 워크플로가 태그를 조립할 때 쓴다"
  value       = aws_ecr_repository.backend.name
}

output "bucket_name" {
  description = "설정·백업 버킷 이름. compute user_data 와 backup 스크립트가 받는다"
  value       = aws_s3_bucket.main.id
}

output "bucket_arn" {
  description = "버킷 ARN"
  value       = aws_s3_bucket.main.arn
}

output "instance_access_policy_arn" {
  description = "compute 의 인스턴스 프로파일에 붙일 정책 ARN"
  value       = aws_iam_policy.instance_access.arn
}

output "uploaded_config_keys" {
  description = "실제로 올라간 설정 파일 키. 비어 있으면 아직 올릴 파일이 없다는 뜻"
  value       = sort([for o in aws_s3_object.config : o.key])
}
