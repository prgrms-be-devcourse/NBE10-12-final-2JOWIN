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

# 10.0.0.0/16 을 피한다. 이 계정은 여러 팀이 공유하고 이미 두 팀이 그 대역을
# 쓰고 있다 — VPC 는 서로 격리돼 있어 동작에 문제는 없지만, 콘솔이나 로그에서
# 10.0.0.x 주소를 봤을 때 우리 것인지 남의 것인지 구분할 수 없게 된다.
#
# CIDR 은 나중에 못 바꾼다. 바꾸려면 VPC 와 그 안의 전부를 재생성해야 한다.
variable "vpc_cidr" {
  description = "VPC CIDR. 공유 계정의 다른 팀과 겹치지 않게 고른다"
  type        = string
  default     = "10.62.0.0/16"
}

variable "public_subnets" {
  description = "퍼블릭 서브넷 (AZ 접미사 -> CIDR). 프라이빗은 만들지 않는다 — NAT 가 없다"
  type        = map(string)
  default = {
    a = "10.62.0.0/24"
    c = "10.62.1.0/24"
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

# ── SSH ──────────────────────────────────────────────────────────────────
# 배포는 SSH 로 한다. 22번은 상시 열지 않고 배포하는 동안만
# 러너 공인 IP /32 를 열었다가 회수한다 — 그건 워크플로가 한다.
#
# 키는 두 개다. 한쪽이 유출돼도 한쪽만 교체하면 되기 때문이다.

variable "key_name" {
  description = "사람 접속용 AWS 키페어 이름. 개인키는 인프라 담당 로컬에만 둔다"
  type        = string
}

variable "deploy_public_key" {
  description = "배포용 SSH 공개키. 개인키는 GitHub Secrets 에 둔다. 공개키라 tfstate 에 들어가도 무방하다"
  type        = string
}
