# ─────────────────────────────────────────────────────────────────────────────
# EC2 인스턴스가 ECR·S3 에 접근할 권한
#
# 정책을 storage 에서 만들고 ARN 만 넘긴다. compute 는 붙이기만 한다 —
# prefix 구조를 아는 쪽이 정책도 갖는 게 맞다.
#
# 핵심: 서버는 백업을 쓸 수만 있고 읽거나 지울 수 없다.
#       침해되어도 과거 백업은 남는다.
#
# 설계 근거: storage.md §4
# ─────────────────────────────────────────────────────────────────────────────

data "aws_iam_policy_document" "instance_access" {

  # ── ECR — pull 만 ─────────────────────────────────────────────────────
  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"] # 계정 단위 액션이라 리소스를 특정할 수 없다
  }

  statement {
    sid    = "EcrPullOnly"
    effect = "Allow"
    actions = [
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchCheckLayerAvailability",
    ]
    resources = [aws_ecr_repository.backend.arn]
  }

  # ── S3 — config 는 읽기, backup 은 쓰기 ────────────────────────────────
  statement {
    sid       = "ReadConfigOnly"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.main.arn}/config/*"]
  }

  # PutObject 만이다. GetObject·DeleteObject 는 주지 않는다.
  statement {
    sid       = "WriteBackupOnly"
    effect    = "Allow"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.main.arn}/backup/*"]
  }

  # 버킷 목록 조회는 두 prefix 안으로만 제한한다.
  statement {
    sid       = "ListWithinPrefixes"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.main.arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["config/*", "backup/*"]
    }
  }
}

resource "aws_iam_policy" "instance_access" {
  name        = "${var.project}-instance-storage"
  description = "EC2 instance profile: ECR pull, read config/ and write backup/ only."
  policy      = data.aws_iam_policy_document.instance_access.json

  tags = {
    Component = "storage"
  }
}
