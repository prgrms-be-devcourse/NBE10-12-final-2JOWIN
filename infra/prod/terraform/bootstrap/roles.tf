# IAM 역할 4종. 하나로 합치면 PR 검사에도 apply 권한이 붙으므로 작업 단위로 쪼갠다.
#
#   2jo-tf-plan     PR         ReadOnlyAccess          plan · Infracost
#   2jo-tf-apply    env:prod   AdministratorAccess     인프라 변경
#   2jo-gha-deploy  env:prod   ECR push + 22번 임시개방  백엔드 배포

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
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
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
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
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
  # develop 머지 후 terraform apply.
  description          = "Infrastructure apply after merge to develop."
  assume_role_policy   = data.aws_iam_policy_document.trust_env_prod.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "tf_apply_admin" {
  role       = aws_iam_role.tf_apply.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AdministratorAccess"
}

# 비용 Deny 가드레일은 두지 않는다. 계정 제공사가 비용을 직접 모니터링하므로
# 중복이라는 협의 결과에 따라 제거했다 (#146).

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

  # 배포는 SSH 로 한다. 22번을 상시 열지 않고, 배포하는 동안만
  # 러너의 공인 IP /32 를 열었다가 회수한다 — 그래서 이 권한이 필요하다.
  #
  # 이 권한이 위험을 늘리지 않는 이유: 이걸 훔칠 수 있는 주체는 같은
  # 파이프라인 안의 SSH 개인키도 이미 가지고 있다. 이미 들어올 수 있는
  # 쪽에 문을 열 권한을 주는 것은 추가 손해가 아니다.
  #
  # 대상은 Project 태그가 붙은 보안그룹으로 좁힌다. SG 는 다른 스택이
  # 만들기 때문에 ARN 을 직접 참조할 수 없다 — 태그가 유일한 연결고리다.
  statement {
    sid    = "ManageSshRuleOnProjectSecurityGroups"
    effect = "Allow"
    actions = [
      "ec2:AuthorizeSecurityGroupIngress",
      "ec2:RevokeSecurityGroupIngress",
    ]
    resources = ["arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:security-group/*"]

    condition {
      test     = "StringEquals"
      variable = "aws:ResourceTag/Project"
      values   = [var.project]
    }
  }

  # 규칙을 회수하려면 규칙 ID 를 알아야 하고, 그건 조회로만 얻는다.
  # 이 두 액션은 리소스 수준 권한을 지원하지 않아 * 가 된다 — 읽기 전용이다.
  statement {
    sid       = "DescribeSecurityGroups"
    effect    = "Allow"
    actions   = ["ec2:DescribeSecurityGroups", "ec2:DescribeSecurityGroupRules"]
    resources = ["*"]
  }
}

resource "aws_iam_role" "gha_deploy" {
  name = "${var.project}-gha-deploy"
  # 백엔드 배포 워크플로. ECR push + 배포 중 22번 임시 개방만.
  description          = "Backend deploy workflow: ECR push and temporary SSH rule on Project=2jo security groups."
  assume_role_policy   = data.aws_iam_policy_document.trust_env_prod.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy" "gha_deploy" {
  name   = "deploy"
  role   = aws_iam_role.gha_deploy.id
  policy = data.aws_iam_policy_document.gha_deploy.json
}
