# ─────────────────────────────────────────────────────────────────────────────
# VPC · 서브넷 · IGW · 라우팅
#
# 원칙: 부대 시설을 만들지 않는다.
#   NAT Gateway 없음     — 월 $32 로 예산의 56%
#   프라이빗 서브넷 없음  — NAT 가 없으면 프라이빗에 둔 인스턴스는 밖으로 못 나간다
#   VPC 엔드포인트 없음   — Interface 기준 개당 월 $7
#
# 설계 근거: network-compute.md §2
# ─────────────────────────────────────────────────────────────────────────────

data "aws_region" "current" {}

resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr

  # 둘 다 명시한다. enable_dns_hostnames 는 terraform 기본값이 false 라
  # 빼면 SSM·ECR 같은 AWS 서비스 도메인 해석이 어긋난다.
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${var.project}-vpc"
  }
}

# 서브넷마다 AWS 가 5개 IP 를 예약한다(네트워크 주소·VPC 라우터·DNS·예약·브로드캐스트).
# /24 면 256 - 5 = 251 개를 쓸 수 있다.
resource "aws_subnet" "public" {
  for_each = var.public_subnets

  vpc_id            = aws_vpc.main.id
  cidr_block        = each.value
  availability_zone = "${data.aws_region.current.region}${each.key}"

  # 기동 직후 cloud-init 이 인터넷에 닿아야 한다. NAT 가 없으므로 공인 IP 가 필수다.
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project}-public-${each.key}"
    Tier = "public"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.project}-igw"
  }
}

# 라우팅 테이블은 하나로 둘 서브넷이 공유한다. 둘 다 퍼블릭이라 경로가 같다.
# 10.0.0.0/16 -> local 경로는 AWS 가 자동으로 넣으므로 여기 쓰지 않는다.
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "${var.project}-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}
