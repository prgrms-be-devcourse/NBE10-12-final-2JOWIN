# cost-guard 모듈 변수 — 설계 근거: cost-guard.md §6.3

variable "project" {
  description = "리소스 이름·태그 접두사"
  type        = string
  default     = "2jo"
}

variable "aws_region" {
  description = "EC2 정지 액션이 대상으로 삼을 리전"
  type        = string
  default     = "ap-northeast-2"
}

# ── 계층 ① 예방 ────────────────────────────────────────────────────

variable "guardrail_policy_name" {
  description = "가드레일 정책 이름. 자기 보호 Statement 가 이 이름으로 만든 ARN 을 참조한다"
  type        = string
  default     = "2jo-cost-guardrail"
}

variable "allowed_instance_types" {
  description = "생성을 허용할 EC2 인스턴스 타입. 목록 밖은 RunInstances 가 거부된다"
  type        = list(string)
  default     = ["t4g.small", "t4g.medium"]

  validation {
    condition     = length(var.allowed_instance_types) > 0
    error_message = "빈 목록이면 모든 인스턴스 생성이 막힌다. 최소 1개는 있어야 한다."
  }
}

variable "allowed_regions" {
  description = <<-EOT
    작업을 허용할 리전.
    us-east-1 은 빼지 말 것 — Budgets·Cost Explorer·CloudFront 같은 글로벌 서비스가
    이 리전으로 보고되므로, 빼면 가드레일 자신과 비용 조회가 막힌다.
  EOT
  type        = list(string)
  default     = ["ap-northeast-2", "us-east-1"]

  validation {
    condition     = contains(var.allowed_regions, "us-east-1")
    error_message = "us-east-1 은 반드시 포함해야 한다. 빼면 Budgets·Cost Explorer 가 막힌다."
  }
}

variable "max_volume_size_gb" {
  description = "EBS 볼륨 1개당 허용 최대 크기(GB)"
  type        = number
  default     = 50
}

variable "terraform_exec_role_name" {
  description = "가드레일을 부착할 Terraform 실행 역할 이름 (GitHub OIDC 로 assume 하는 역할)"
  type        = string
}

variable "developer_user_names" {
  description = "가드레일을 부착할 개발자 IAM 사용자. 콘솔에서의 수동 생성도 같이 막는다"
  type        = list(string)
  default     = []
}

# ── 계층 ②·③ 감지와 차단 ──────────────────────────────────────────

variable "budget_limit_usd" {
  description = "하드 한도(USD)"
  type        = number
  default     = 50
}

variable "warn_thresholds" {
  description = "Discord 알림만 보내는 실측 임계(%)"
  type        = list(number)
  default     = [60, 80]
}

variable "freeze_threshold" {
  description = "신규 리소스 생성을 봉쇄할 실측 임계(%)"
  type        = number
  default     = 90
}

variable "stop_threshold" {
  description = "EC2 를 정지할 실측 임계(%)"
  type        = number
  default     = 100
}

variable "stop_instance_ids" {
  description = "정지 대상 인스턴스 ID. 비면 정지 액션을 만들지 않는다"
  type        = list(string)
  default     = []
}

variable "enable_budget_actions" {
  description = "Budget Action 2종 생성 여부. Organizations 제약 시 false"
  type        = bool
  default     = true
}

variable "discord_webhook_ssm_path" {
  description = "Lambda 가 런타임에 읽을 웹훅 URL 의 SSM 경로. 값은 수동 주입한다"
  type        = string
  default     = "/2jo/prod/discord-webhook-infra"
}

variable "log_retention_days" {
  description = "Lambda 로그 보존 일수. 길게 잡으면 CloudWatch 요금이 붙는다"
  type        = number
  default     = 7
}
