variable "aws_region" {
  description = "리소스를 만들 기본 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  description = "리소스 이름·태그 접두사. cost-guard 의 태그 조건과 같은 값이어야 한다"
  type        = string
  default     = "2jo"
}

# ── 비용 가드레일 ──────────────────────────────────────────────────
# 근거: cost-guard.md §6.3

variable "budget_limit_usd" {
  description = "하드 한도. 실제 예산 ₩80,000(≈$57)보다 낮게 잡아 8~24h 감지 지연을 흡수한다"
  type        = number
  default     = 50
}

variable "warn_thresholds" {
  description = "Discord 알림만 보내는 실측 임계(%). 차단은 하지 않는다"
  type        = list(number)
  default     = [60, 80]
}

variable "freeze_threshold" {
  description = "신규 리소스 생성을 봉쇄할 실측 임계(%). 서비스는 계속 돈다"
  type        = number
  default     = 90
}

variable "stop_threshold" {
  description = "EC2 를 정지할 실측 임계(%). 서비스가 멈춘다"
  type        = number
  default     = 100
}

variable "stop_instance_ids" {
  description = <<-EOT
    100% 도달 시 정지할 인스턴스 ID.
    Budget Action 은 태그를 못 받고 ID 만 받는다 — compute 모듈 생성 후 채운다.
    비어 있으면 정지 액션 자체를 만들지 않는다 (cost-guard.md §6.5 배포 순서).
  EOT
  type        = list(string)
  default     = []
}

variable "enable_budget_actions" {
  description = <<-EOT
    Budget Action 2종(90% 봉쇄 · 100% 정지) 생성 여부.
    계정이 Organizations 멤버면 생성이 거부될 수 있다 — 그때 false 로 내리고
    일일 잡 백스톱에 맡긴다 (cost-guard.md §5.3 · §7.2 #6).
  EOT
  type        = bool
  default     = true
}

variable "terraform_exec_role_name" {
  description = "가드레일과 90% 봉쇄 정책을 붙일 역할. bootstrap 의 tf_apply_role_name 출력값"
  type        = string
  default     = "2jo-tf-apply"
}

variable "developer_user_names" {
  description = "가드레일을 함께 붙일 IAM 사용자. 콘솔에서의 수동 생성도 같이 막는다"
  type        = list(string)
  default     = []
}

variable "allowed_instance_types" {
  description = "생성을 허용할 EC2 인스턴스 타입"
  type        = list(string)
  default     = ["t4g.small", "t4g.medium"]
}

variable "allowed_regions" {
  description = "작업을 허용할 리전. us-east-1 은 글로벌 서비스 때문에 빼면 안 된다"
  type        = list(string)
  default     = ["ap-northeast-2", "us-east-1"]
}

variable "max_volume_size_gb" {
  description = "EBS 볼륨 1개당 허용 최대 크기(GB). prod 는 40GB 를 쓴다"
  type        = number
  default     = 50
}

variable "discord_webhook_ssm_path" {
  description = <<-EOT
    Lambda 가 런타임에 읽을 Discord 웹훅 URL 의 SSM 경로.
    값은 terraform 으로 만들지 않는다 — tfstate 에 평문으로 남기 때문이다.
      aws ssm put-parameter --name <경로> --type SecureString --value <URL>
  EOT
  type        = string
  default     = "/2jo/prod/discord-webhook-infra"
}
