# ─────────────────────────────────────────────────────────────────────────────
# 인스턴스 프로파일
#
# 서버에 액세스 키를 심지 않는다. 모든 AWS 접근이 이 프로파일 경유다.
# 키 파일이 없으면 유출될 것도, 회전할 것도 없다.
#
# 설계 근거: network-compute.md §3.5
# ─────────────────────────────────────────────────────────────────────────────

data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

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
  description        = "EC2 instance profile: SSM, ECR pull, S3 config and backup, SSM parameters."
  assume_role_policy = data.aws_iam_policy_document.instance_trust.json

  tags = {
    Component = "compute"
  }
}

# Session Manager 와 SendCommand. 이게 있어서 22번 포트를 안 열어도 된다.
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# ECR pull + S3 config/ 읽기 + backup/ 쓰기.
# 정책 본체는 storage 모듈에 있다 — prefix 구조를 아는 쪽이 정책도 갖는다.
resource "aws_iam_role_policy_attachment" "storage" {
  role       = aws_iam_role.instance.name
  policy_arn = var.storage_policy_arn
}

# 시크릿은 terraform 으로 만들지 않는다(tfstate 가 평문이다).
# 사람이 SecureString 으로 넣어둔 값을 fetch-secrets.sh 가 런타임에 읽는다.
data "aws_iam_policy_document" "ssm_parameters" {
  statement {
    sid    = "ReadProjectParameters"
    effect = "Allow"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]
    resources = ["arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${var.ssm_parameter_prefix}/*"]
  }

  # SecureString 복호화. kms:ViaService 로 SSM 경유만 허용한다 —
  # 이게 없으면 이 역할로 계정의 다른 KMS 대상까지 복호화할 수 있다.
  statement {
    sid       = "DecryptSecureString"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.aws_region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "ssm_parameters" {
  name   = "ssm-parameters"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.ssm_parameters.json
}

resource "aws_iam_instance_profile" "instance" {
  name = "${var.project}-instance"
  role = aws_iam_role.instance.name
}
