# ─────────────────────────────────────────────────────────────────────────────
# ECR — 백엔드 이미지 저장소 (linux/amd64)
#
# 태그는 git SHA 만 쓴다. latest 를 버리면 IMMUTABLE 을 켤 수 있고,
# 그러면 "직전 SHA 로 롤백"이 언제나 같은 이미지를 가리킨다.
# latest 를 쓰면 누군가 재push 했을 때 롤백 대상이 조용히 바뀐다.
#
# 설계 근거: storage.md §2
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_ecr_repository" "backend" {
  name = var.ecr_repository_name

  # 같은 태그로 두 번 push 할 수 없다. 롤백 대상이 불변이 되는 근거.
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true # ECR 기본 스캔은 무료다. Inspector 확장 스캔이 유료
  }

  encryption_configuration {
    encryption_type = "AES256" # KMS 는 키당 월 $1
  }

  tags = {
    Component = "storage"
  }
}

# 수명주기 정책이 곧 비용 통제다. 없으면 이미지가 무한히 쌓인다.
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [
      {
        # 빌드 실패·재push 로 생기는 고아 레이어. 눈에 안 보이는데 과금된다.
        rulePriority = 1
        description  = "Expire untagged images after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        # tagStatus = "any" 규칙은 반드시 마지막이어야 한다.
        # 앞에 두면 이 규칙이 전부 먹어버려서 뒤 규칙이 평가되지 않는다.
        rulePriority = 2
        description  = "Keep only the most recent images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.image_retention_count
        }
        action = { type = "expire" }
      },
    ]
  })
}
