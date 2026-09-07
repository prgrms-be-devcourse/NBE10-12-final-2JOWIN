# network 모듈 변수 — 설계 근거: network-compute.md §2

variable "project" {
  description = "리소스 이름 접두사"
  type        = string
  default     = "2jo"
}

variable "vpc_cidr" {
  description = "VPC CIDR. /16 이면 65,536 IP — 서브넷을 더 쪼갤 여지를 남긴다"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnets" {
  description = <<-EOT
    퍼블릭 서브넷. 키가 AZ 접미사(a·c), 값이 CIDR.
    프라이빗 서브넷은 만들지 않는다 — NAT 가 없어서 프라이빗에 두면 밖으로 못 나간다.
  EOT
  type        = map(string)
  default = {
    a = "10.0.0.0/24" # EC2 가 들어간다
    c = "10.0.1.0/24" # 예비 — 비어 있다. 서브넷 자체는 과금되지 않는다
  }

  validation {
    condition     = length(var.public_subnets) >= 1
    error_message = "퍼블릭 서브넷이 최소 1개는 있어야 한다."
  }
}

variable "primary_az_suffix" {
  description = "EC2 를 둘 AZ 접미사. public_subnets 의 키 중 하나여야 한다"
  type        = string
  default     = "a"
}
