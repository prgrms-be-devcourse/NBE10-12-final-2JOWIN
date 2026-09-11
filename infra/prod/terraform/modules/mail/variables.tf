# mail 모듈 변수 — 설계 근거: mail.md §4 · §5

variable "project" {
  description = "리소스 이름 접두사"
  type        = string
  default     = "2jo"
}

variable "aws_region" {
  description = "SES 리전. SMTP 엔드포인트 주소가 여기서 나온다"
  type        = string
  default     = "ap-northeast-2"
}

variable "domain" {
  description = "발신에 쓸 도메인. 이 이름으로 DKIM 토큰이 발급된다"
  type        = string
  default     = "jomin4.cloud"

  validation {
    # 주소를 넣으면 도메인 자격이 아니라 주소 자격이 만들어진다. 그러면
    # DKIM 토큰이 나오지 않아 dnszi 등록 단계가 통째로 무의미해진다.
    condition     = !strcontains(var.domain, "@")
    error_message = "도메인만 넣는다. 주소(@ 포함)가 아니다."
  }
}
