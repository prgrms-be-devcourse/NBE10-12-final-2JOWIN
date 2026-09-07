# 모든 terraform 스택의 상태가 이 버킷 하나에 들어간다.
# 이 버킷을 잃으면 인프라 전체의 상태를 잃는다 — 방어를 겹쳐 둔다.

locals {
  # S3 버킷 이름은 전역 유일해야 한다. 계정 ID를 붙여 충돌을 피한다.
  state_bucket_name = "${var.project}-tfstate-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket" "tfstate" {
  bucket = local.state_bucket_name

  # 방어 1 — 코드로 destroy 를 막는다. 지우려면 이 블록을 먼저 지워야 한다.
  lifecycle {
    prevent_destroy = true
  }
}

# 방어 2 — 상태가 깨져도 이전 버전으로 되돌린다.
resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

# SSE-S3(AES256)를 쓴다. KMS 는 키 하나당 월 $1 — 예산 8만원의 1.4%라 안 쓴다.
resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 방어 3 — 상태 파일 유출은 인프라 전체 노출이다. 4종 전부 막는다.
resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 방어 4 — 평문 HTTP 요청을 거부한다.
data "aws_iam_policy_document" "tfstate" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.tfstate.arn, "${aws_s3_bucket.tfstate.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  policy = data.aws_iam_policy_document.tfstate.json

  # 퍼블릭 차단이 먼저 걸린 뒤에 정책을 붙인다 (순서 사고 방지)
  depends_on = [aws_s3_bucket_public_access_block.tfstate]
}

# 버저닝을 켜면 이전 버전이 무한히 쌓인다. 90일로 끊는다.
resource "aws_s3_bucket_lifecycle_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.state_version_retention_days
    }

    # 중단된 멀티파트 업로드도 과금된다. 하루 뒤 정리.
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }

  depends_on = [aws_s3_bucket_versioning.tfstate]
}
