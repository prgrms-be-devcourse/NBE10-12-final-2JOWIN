# ─────────────────────────────────────────────────────────────────────────────
# 인스턴스 프로파일
#
# 서버에 액세스 키를 심지 않는다. 모든 AWS 접근이 이 프로파일 경유다.
# 키 파일이 없으면 유출될 것도, 회전할 것도 없다.
#
# SSH 전환으로 SSM 권한 두 건(Session Manager · 파라미터 읽기)이 빠졌다.
# 서버가 AWS 에 대해 갖는 권한은 ECR pull 과 S3 두 prefix 뿐이다.
#
# 설계 근거: network-compute.md §3.5
# ─────────────────────────────────────────────────────────────────────────────

data "aws_iam_policy_document" "instance_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name               = "${var.project}-instance"
  description        = "EC2 instance profile: ECR pull and S3 config/backup access only."
  assume_role_policy = data.aws_iam_policy_document.instance_trust.json

  tags = {
    Component = "compute"
  }
}

# ECR pull + S3 config/ 읽기 + backup/ 쓰기.
# 정책 본체는 storage 모듈에 있다 — prefix 구조를 아는 쪽이 정책도 갖는다.
resource "aws_iam_role_policy_attachment" "storage" {
  role       = aws_iam_role.instance.name
  policy_arn = var.storage_policy_arn
}

resource "aws_iam_instance_profile" "instance" {
  name = "${var.project}-instance"
  role = aws_iam_role.instance.name
}

# SES 발송. 정책 본체는 mail 모듈에 있다 — 자격을 아는 쪽이 정책도 갖는다.
#
# 이 권한은 컨테이너가 IMDS 에 닿을 수 있어야 쓸모가 있다. instance.tf 의
# hop limit 이 그 조건이고, 둘은 같이 움직여야 한다 (#346).
resource "aws_iam_role_policy_attachment" "mail" {
  role       = aws_iam_role.instance.name
  policy_arn = var.mail_policy_arn
}
