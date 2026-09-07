# storage 모듈 변수 — 설계 근거: storage.md §6

variable "project" {
  description = "리소스 이름 접두사"
  type        = string
  default     = "2jo"
}

variable "ecr_repository_name" {
  description = "백엔드 이미지 저장소 이름"
  type        = string
  default     = "2jo/backend"
}

variable "image_retention_count" {
  description = "유지할 이미지 개수. 그대로 롤백 가능 깊이가 된다"
  type        = number
  default     = 10

  validation {
    condition     = var.image_retention_count >= 2
    error_message = "최소 2개는 있어야 직전 버전으로 롤백할 수 있다."
  }
}

variable "bucket_name_prefix" {
  description = "S3 버킷 이름 접두사. 뒤에 계정 ID 가 붙는다 (버킷 이름은 전역 유일)"
  type        = string
  default     = "2jo-prod"
}

variable "backup_retention_days" {
  description = "backup/ prefix 보관 일수. 데모 데이터라 길게 둘 이유가 없다"
  type        = number
  default     = 7
}

variable "config_source_root" {
  description = <<-EOT
    설정 번들을 읽어올 로컬 루트 (보통 infra/prod).
    이 아래의 config_dirs 를 S3 config/ 로 그대로 올린다.
  EOT
  type        = string
}

variable "config_dirs" {
  description = "config_source_root 아래에서 업로드할 디렉터리. 비어 있으면 아무것도 올리지 않는다"
  type        = list(string)
  default     = ["compose", "caddy", "scripts", "monitoring"]
}
