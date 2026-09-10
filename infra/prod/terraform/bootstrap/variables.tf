variable "aws_region" {
  description = "모든 리소스를 만들 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  description = "리소스 이름·태그 접두사. 회사 계정 공유 규칙상 모든 리소스가 이 값으로 시작한다"
  type        = string
  default     = "2jo"
}

variable "github_sub_prefix" {
  description = <<-EOT
    OIDC 토큰 sub 클레임의 접두사. 신뢰 조건에 StringEquals 로 들어간다.

    org/repo 를 그대로 쓰지 않는 이유: 이 조직은 subject 에 숫자 ID 를
    함께 넣는 형식을 쓴다(org 이름이 바뀌어도 신뢰가 흔들리지 않게 하는
    GitHub 기능). 실제 값은 다음으로 확인한다.

      gh api repos/<org>/<repo>/actions/oidc/customization/sub
  EOT
  type        = string
  default     = "repo:prgrms-be-devcourse@88020948/NBE10-12-final-2JOWIN@1347278714"

  validation {
    condition     = startswith(var.github_sub_prefix, "repo:")
    error_message = "github_sub_prefix 는 repo: 로 시작해야 한다."
  }
}

variable "deploy_environment" {
  description = <<-EOT
    백엔드 배포가 쓰는 GitHub Environment. 승인자를 두지 않는다.

    infra_environment 와 나눈 이유는 terraform-apply 가 그쪽을 쓰기
    때문이다. 한 환경에서 승인을 빼면 인프라 apply 의 게이트까지 함께
    풀린다.
  EOT
  type        = string
  default     = "backend-deploy"
}

variable "prod_environment" {
  description = "terraform apply 가 쓰는 GitHub Environment 이름. 승인 게이트가 걸린 그 이름"
  type        = string
  default     = "infra-apply"
}

# ── 이름 교체 중에만 쓰는 변수 2종 (#270) ────────────────────────
# 환경 이름은 OIDC 토큰의 sub 에 들어가 IAM 권한 경계가 된다. GitHub 에
# 이름 변경 기능이 없어 「새로 만들고 옛것을 지우는」 방식이 되는데, 그
# 사이 배포가 끊기지 않도록 옛 이름을 함께 받는다.
#
# 존재하지 않는 환경을 허용해도 권한은 늘지 않는다 — 환경이 없으면 그
# sub 를 가진 토큰 자체가 발급되지 않는다.
#
# 교체가 끝나면 이 두 변수와 참조를 지운다 (#270 5단계).

variable "prod_environment_legacy" {
  description = "교체 전 인프라 apply 환경 이름. 5단계에서 삭제한다"
  type        = string
  default     = "prod"
}

variable "deploy_environment_legacy" {
  description = "교체 전 백엔드 배포 환경 이름. 5단계에서 삭제한다"
  type        = string
  default     = "prod-deploy"
}

variable "ecr_repository_name" {
  description = "배포 역할이 push 할 수 있는 유일한 ECR 리포지토리. storage 모듈이 실제로 만든다"
  type        = string
  default     = "2jo-backend"
}

variable "state_version_retention_days" {
  description = "상태 파일 이전 버전 보관 일수. 버전이 무한 누적되는 것을 막는다"
  type        = number
  default     = 90
}
