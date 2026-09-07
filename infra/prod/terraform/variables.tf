variable "aws_region" {
  description = "리소스를 만들 기본 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  description = "리소스 이름·태그 접두사. 회사 계정 공유 규칙상 모든 리소스가 이 값으로 시작한다"
  type        = string
  default     = "2jo"
}

# ── 네트워크 ───────────────────────────────────────────────────────
# 근거: network-compute.md §2

variable "vpc_cidr" {
  description = "VPC CIDR"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnets" {
  description = "퍼블릭 서브넷 (AZ 접미사 -> CIDR). 프라이빗은 만들지 않는다 — NAT 가 없다"
  type        = map(string)
  default = {
    a = "10.0.0.0/24"
    c = "10.0.1.0/24"
  }
}

variable "primary_az_suffix" {
  description = "EC2 를 둘 AZ 접미사"
  type        = string
  default     = "a"
}

# ── 저장소 ─────────────────────────────────────────────────────────
# 근거: storage.md §6

variable "ecr_repository_name" {
  description = "백엔드 이미지 저장소 이름"
  type        = string
  default     = "2jo-backend"
}

variable "image_retention_count" {
  description = "유지할 이미지 개수 = 롤백 가능 깊이"
  type        = number
  default     = 10
}

variable "bucket_name_prefix" {
  description = "설정·백업 버킷 이름 접두사. 뒤에 계정 ID 가 붙는다"
  type        = string
  default     = "2jo-prod"
}

variable "backup_retention_days" {
  description = "backup/ 보관 일수"
  type        = number
  default     = 7
}

# ── 컴퓨트 ─────────────────────────────────────────────────────────
# 근거: network-compute.md §3

variable "instance_type" {
  description = "EC2 인스턴스 타입"
  type        = string
  default     = "t3.medium"
}

variable "root_volume_size_gb" {
  description = "루트 EBS 크기(GB)"
  type        = number
  default     = 40
}

variable "swap_size_gb" {
  description = "스왑파일 크기(GB). 4 GiB 압박의 안전망"
  type        = number
  default     = 2
}
