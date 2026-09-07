# ─────────────────────────────────────────────────────────────────────────────
# S3 — 버킷 하나를 prefix 로 나눠 쓴다
#
#   config/   compose · Caddyfile · scripts · monitoring   서버가 읽는다
#   backup/   야간 pg_dump gzip                            서버가 쓴다 (읽지도 지우지도 못한다)
#
# 버킷을 둘로 나누지 않은 이유: 정책·수명주기·출력이 두 벌이 되는데,
# prefix 로 IAM 과 수명주기를 똑같이 나눌 수 있어서 얻는 게 없다.
# tfstate 만은 bootstrap 의 별도 버킷에 둔다 — 폭발 반경을 분리해야 한다.
#
# 설계 근거: storage.md §3
# ─────────────────────────────────────────────────────────────────────────────

data "aws_caller_identity" "current" {}

locals {
  bucket_name = "${var.bucket_name_prefix}-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket" "main" {
  bucket = local.bucket_name

  # 실수로 지우지 않도록. 안에 내용이 있으면 destroy 가 실패한다.
  force_destroy = false

  tags = {
    Component = "storage"
  }
}

# Caddyfile 을 잘못 올렸을 때 되돌릴 수 있어야 한다.
resource "aws_s3_bucket_versioning" "main" {
  bucket = aws_s3_bucket.main.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "main" {
  bucket = aws_s3_bucket.main.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256" # KMS 는 키당 월 $1
    }
  }
}

resource "aws_s3_bucket_public_access_block" "main" {
  bucket = aws_s3_bucket.main.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

data "aws_iam_policy_document" "main" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.main.arn, "${aws_s3_bucket.main.arn}/*"]

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

resource "aws_s3_bucket_policy" "main" {
  bucket = aws_s3_bucket.main.id
  policy = data.aws_iam_policy_document.main.json

  depends_on = [aws_s3_bucket_public_access_block.main]
}

resource "aws_s3_bucket_lifecycle_configuration" "main" {
  bucket = aws_s3_bucket.main.id

  rule {
    id     = "expire-backups"
    status = "Enabled"

    filter {
      prefix = "backup/"
    }

    expiration {
      days = var.backup_retention_days
    }
  }

  # 버저닝을 켜면 이전 버전이 무한히 쌓인다.
  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.backup_retention_days
    }
  }

  # 중단된 멀티파트 업로드는 콘솔에도 ls 에도 안 보이는데 스토리지 요금이 나간다.
  # 넣지 않으면 원인 불명 비용이 된다.
  rule {
    id     = "abort-incomplete-multipart"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }

  depends_on = [aws_s3_bucket_versioning.main]
}
