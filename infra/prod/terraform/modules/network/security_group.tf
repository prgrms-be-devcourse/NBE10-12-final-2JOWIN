# ─────────────────────────────────────────────────────────────────────────────
# 보안그룹 — 상시 열린 인바운드는 80·443 뿐이다.
#
# 22번은 여기에 선언하지 않는다. 배포 워크플로가 배포하는 동안만
# 러너 공인 IP /32 를 열었다가 회수한다.
#
# 규칙을 인라인 ingress/egress 블록이 아니라 별도 리소스로 쓰는 것이 그 전제다.
# 인라인이면 terraform 이 SG 전체를 조정 대상으로 보고, 다음 apply 가
# 런타임에 추가된 22번 규칙을 조용히 되돌린다. 콘솔에서 손으로 추가한
# 규칙이 diff 에 안 보이는 문제도 같은 이유다.
#
# 설계 근거: network-compute.md §2.4
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_security_group" "web" {
  name        = "${var.project}-web"
  description = "Inbound 80 and 443 only. Port 22 is opened at deploy time by the workflow and revoked after."
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project}-web"
  }
}

# 80 을 여는 이유는 두 가지다.
#   1. Let's Encrypt HTTP-01 챌린지 — Caddy 가 인증서를 받으려면 필요하다
#
# description 에 아포스트로피를 쓰지 않는다. AWS 가 보안그룹 규칙 설명에
# 받는 문자를 제한하는데 ' 가 거기 없다 — plan 은 통과하고 apply 가
# InvalidParameterValue 로 죽는다. .github/scripts/check-sg-descriptions.py
# 가 이걸 검사한다.
#   2. HTTPS 리다이렉트 — http:// 로 들어온 사람을 https:// 로 보낸다
resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.web.id
  description       = "HTTP: ACME HTTP-01 challenge and redirect to HTTPS"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.web.id
  description       = "HTTPS"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

# HTTP/3(QUIC)는 UDP 443 을 쓴다. Caddy 가 기본으로 켠다.
# 이걸 빼면 브라우저가 조용히 TCP 로 내려앉기 때문에 장애로 보이지 않는다 —
# 그래서 빠뜨리기 쉽다.
resource "aws_vpc_security_group_ingress_rule" "http3_quic" {
  security_group_id = aws_security_group.web.id
  description       = "HTTP/3 (QUIC)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "udp"
  from_port         = 443
  to_port           = 443
}

# 아웃바운드는 전부 연다.
# NAT 도 VPC 엔드포인트도 없어서 ECR pull · S3 · Let's Encrypt 가
# 전부 IGW 를 통해 나간다. 좁히려면 그 부대 시설을 사야 하는데,
# 그게 이 프로젝트가 예산 때문에 포기한 바로 그것이다.
resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.web.id
  description       = "All outbound: ECR, S3 and ACME egress via IGW"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# AWS 가 VPC 마다 자동으로 만드는 기본 보안그룹.
# 그냥 두면 "같은 SG 안에서는 전부 허용" 규칙이 남아 있고, 누군가 실수로
# 이 SG 를 붙이면 조용히 열린다. terraform 이 소유하고 규칙을 전부 비운다.
#
# ingress/egress 블록을 쓰지 않는 것이 곧 "규칙 없음"이다.
resource "aws_default_security_group" "default" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.project}-default-unused"
  }
}
