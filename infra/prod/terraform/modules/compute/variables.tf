# compute 모듈 변수 — 설계 근거: network-compute.md §3 · §6

variable "project" {
  description = "리소스 이름 접두사"
  type        = string
  default     = "2jo"
}

variable "aws_region" {
  description = "인스턴스가 있는 리전. user_data 의 aws CLI 호출에 쓴다"
  type        = string
  default     = "ap-northeast-2"
}

variable "instance_type" {
  description = "EC2 인스턴스 타입"
  type        = string
  default     = "t3.medium" # 2 vCPU · 4 GiB · x86_64
}

variable "root_volume_size_gb" {
  description = "루트 EBS 크기(GB)"
  type        = number
  default     = 40

  validation {
    condition     = var.root_volume_size_gb >= 20
    error_message = "컨테이너 9종 + 로그 + TSDB 를 담으려면 최소 20GB 는 필요하다."
  }
}

variable "swap_size_gb" {
  description = "스왑파일 크기(GB). 4 GiB 압박의 안전망이고 추가 비용은 없다"
  type        = number
  default     = 2
}

variable "subnet_id" {
  description = "인스턴스를 둘 퍼블릭 서브넷. network 모듈 출력"
  type        = string
}

variable "security_group_id" {
  description = "80·443 만 열린 보안그룹. network 모듈 출력"
  type        = string
}

variable "config_bucket" {
  description = "설정 번들과 백업이 있는 버킷 이름. storage 모듈 출력"
  type        = string
}

variable "storage_policy_arn" {
  description = "ECR pull · S3 prefix 접근 정책 ARN. storage 모듈 출력"
  type        = string
}

variable "ssm_parameter_prefix" {
  description = "인스턴스가 읽을 수 있는 SSM 파라미터 경로. 값은 수동 주입한다"
  type        = string
  default     = "/2jo/prod"
}

variable "app_dir" {
  description = "서버에서 설정 번들과 compose 파일이 놓일 경로"
  type        = string
  default     = "/opt/2jo"
}
