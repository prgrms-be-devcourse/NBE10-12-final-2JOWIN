variable "aws_region" {
  description = "모든 리소스를 만들 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  description = "리소스 이름·태그 접두사. cost-guard 의 태그 조건과 반드시 같은 값"
  type        = string
  default     = "2jo"
}

variable "github_repo" {
  description = "OIDC 신뢰 대상 레포 (org/repo). 신뢰 정책의 sub 조건에 그대로 들어간다"
  type        = string
  default     = "prgrms-be-devcourse/NBE10-12-final-2JOWIN"

  validation {
    condition     = can(regex("^[^/]+/[^/]+$", var.github_repo))
    error_message = "github_repo 는 org/repo 형식이어야 한다."
  }
}

variable "prod_environment" {
  description = "apply·배포에 쓰는 GitHub Environment 이름. 승인 게이트가 걸린 그 이름"
  type        = string
  default     = "prod"
}

variable "cost_environment" {
  description = "일일 비용 잡이 쓰는 GitHub Environment 이름"
  type        = string
  default     = "cost-guard"
}

variable "ecr_repository_name" {
  description = "배포 역할이 push 할 수 있는 유일한 ECR 리포지토리. storage 모듈이 실제로 만든다"
  type        = string
  default     = "2jo/backend"
}

variable "state_version_retention_days" {
  description = "상태 파일 이전 버전 보관 일수. 버전이 무한 누적되는 것을 막는다"
  type        = number
  default     = 90
}
