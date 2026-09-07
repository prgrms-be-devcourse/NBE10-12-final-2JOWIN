# IAM 역할 4종. 하나로 합치면 PR 검사에도 apply 권한이 붙으므로 작업 단위로 쪼갠다.
#
#   2jo-tf-plan     PR         ReadOnlyAccess          plan · Infracost
#   2jo-tf-apply    env:prod   AdministratorAccess     인프라 변경
#   2jo-gha-deploy  env:prod   ECR push + SSM 실행     백엔드 배포
#   2jo-gha-cost    env:cost-guard  비용 조회 + 정지   일일 비용 잡

locals {
  oidc_host = "token.actions.githubusercontent.com"

  ecr_repo_arn = "arn:${data.aws_partition.current.partition}:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/${var.ecr_repository_name}"
}

# ── 신뢰 정책 3종 ────────────────────────────────────────────────
# sub 조건이 이 스택 전체의 보안 경계다. 여기가 느슨하면 나머지가 무의미하다.

# PR 잡용. fork PR 도 이 sub 를 갖지만, 팀은 같은 레포에 브랜치로 작업한다(docs/13 §0.1).
data "aws_iam_policy_document" "trust_pull_request" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:sub"
      values   = ["repo:${var.github_repo}:pull_request"]
    }
  }
}

# Environment 기준을 쓰는 이유: Environment 에 승인 게이트를 걸면
# 승인 전에는 OIDC 토큰 자체가 발급되지 않는다. 브랜치 기준으로는 그게 안 된다.
data "aws_iam_policy_document" "trust_env_prod" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:sub"
      values   = ["repo:${var.github_repo}:environment:${var.prod_environment}"]
    }
  }
}

data "aws_iam_policy_document" "trust_env_cost" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:sub"
      values   = ["repo:${var.github_repo}:environment:${var.cost_environment}"]
    }
  }
}

# ── 1. tf-plan — PR 의 plan · Infracost ──────────────────────────
resource "aws_iam_role" "tf_plan" {
  name = "${var.project}-tf-plan"
  # PR 의 terraform plan · Infracost 전용. 읽기 + 상태 락 파일만.
  # description 은 AWS 가 허용 문자를 제한해 영문으로 둔다(한글 불가).
  description          = "PR-only: terraform plan and Infracost. Read-only plus state lock file."
  assume_role_policy   = data.aws_iam_policy_document.trust_pull_request.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "tf_plan_readonly" {
  role       = aws_iam_role.tf_plan.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/ReadOnlyAccess"
}

# ReadOnlyAccess 에는 쓰기가 없는데, plan 도 S3 네이티브 락 파일은 만들어야 한다.
# .tflock 객체에만 쓰기를 연다.
# -lock=false 로 우회하지 않는 이유: 플래그를 한 번 빠뜨리면 AccessDenied 가
# 설정 오류처럼 보여 디버깅이 길어진다.
data "aws_iam_policy_document" "tf_plan_lock" {
  statement {
    sid       = "StateLockFileOnly"
    effect    = "Allow"
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.tfstate.arn}/*.tflock"]
  }
}

resource "aws_iam_role_policy" "tf_plan_lock" {
  name   = "state-lock"
  role   = aws_iam_role.tf_plan.id
  policy = data.aws_iam_policy_document.tf_plan_lock.json
}

# ── 2. tf-apply — 인프라 변경 ────────────────────────────────────
# Admin + Deny 가드레일 조합이다. 최소 권한 열거는 4주 안에 유지가 불가능하다.
# 경계를 "뭐든 만들 수 있지만 비싼 건 못 만든다" 로 잡는다.
resource "aws_iam_role" "tf_apply" {
  name = "${var.project}-tf-apply"
  # develop 머지 후 terraform apply. cost-guard 의 Deny 정책이 여기에 붙는다.
  description          = "Infrastructure apply after merge to develop. cost-guard Deny policy attaches here."
  assume_role_policy   = data.aws_iam_policy_document.trust_env_prod.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "tf_apply_admin" {
  role       = aws_iam_role.tf_apply.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AdministratorAccess"
}

# 비싼 리소스를 막는 Deny 가드레일은 여기가 아니라 cost-guard 모듈이 붙인다.
# 이 스택에 넣으면 가드레일을 고칠 때마다 bootstrap 을 다시 돌려야 한다.
# 연결 고리: outputs.tf 의 tf_apply_role_name

# ── 3. gha-deploy — 백엔드 배포 ──────────────────────────────────
# tf-apply 로 배포까지 겸하면 배포 워크플로에 인프라 전체 변경 권한이 붙는다. 분리한다.
data "aws_iam_policy_document" "gha_deploy" {
  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"] # 이 액션은 리소스를 특정할 수 없다
  }

  statement {
    sid    = "EcrPushToBackendRepoOnly"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
    ]
    resources = [local.ecr_repo_arn]
  }

  # ssm:SendCommand 는 문서와 인스턴스 양쪽을 Resource 로 요구한다.
  # 태그 조건을 문서 쪽에도 걸면 문서에는 그 태그가 없어 전체가 거부된다 — 문장을 나눈다.
  statement {
    sid       = "SendCommandDocument"
    effect    = "Allow"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}::document/AWS-RunShellScript"]
  }

  statement {
    sid       = "SendCommandProjectInstancesOnly"
    effect    = "Allow"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/*"]

    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/Project"
      values   = [var.project]
    }
  }

  # 배포 결과 폴링. 커맨드 ID 는 실행 시점에 정해져 리소스를 좁힐 수 없다.
  statement {
    sid       = "PollCommandResult"
    effect    = "Allow"
    actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
    resources = ["*"]
  }
}

resource "aws_iam_role" "gha_deploy" {
  name = "${var.project}-gha-deploy"
  # 백엔드 배포 워크플로. ECR push + 2jo 인스턴스에 SSM 실행만.
  description          = "Backend deploy workflow: ECR push and SSM run on Project=2jo instances only."
  assume_role_policy   = data.aws_iam_policy_document.trust_env_prod.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy" "gha_deploy" {
  name   = "deploy"
  role   = aws_iam_role.gha_deploy.id
  policy = data.aws_iam_policy_document.gha_deploy.json
}

# ── 4. gha-cost — 일일 비용 잡 ───────────────────────────────────
# Budgets Action 이 Organizations 제약으로 막혀도 이 잡이 백스톱으로 정지시킨다.
data "aws_iam_policy_document" "gha_cost" {
  statement {
    sid       = "ReadCost"
    effect    = "Allow"
    actions   = ["ce:GetCostAndUsage", "ce:GetCostForecast"]
    resources = ["*"] # Cost Explorer 는 리소스 수준 권한이 없다
  }

  # 회사 계정이라 다른 팀 인스턴스가 있을 수 있다. 태그로 2jo 것만 멈춘다.
  statement {
    sid       = "StopProjectInstancesOnly"
    effect    = "Allow"
    actions   = ["ec2:StopInstances"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "ec2:ResourceTag/Project"
      values   = [var.project]
    }
  }

  statement {
    sid       = "DescribeForVerification"
    effect    = "Allow"
    actions   = ["ec2:DescribeInstances"]
    resources = ["*"] # Describe 계열은 리소스 수준 권한을 지원하지 않는다
  }
}

resource "aws_iam_role" "gha_cost" {
  name = "${var.project}-gha-cost"
  # 일일 누적 비용 잡. 비용 조회 + 2jo 인스턴스 정지만.
  description          = "Daily cumulative cost job: read Cost Explorer and stop Project=2jo instances."
  assume_role_policy   = data.aws_iam_policy_document.trust_env_cost.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy" "gha_cost" {
  name   = "cost-report"
  role   = aws_iam_role.gha_cost.id
  policy = data.aws_iam_policy_document.gha_cost.json
}
