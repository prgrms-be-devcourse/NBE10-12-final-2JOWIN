# ─────────────────────────────────────────────────────────────────────────────
# SMTP 발송용 IAM 사용자
#
# SES SMTP 자격증명은 IAM 액세스 키의 다른 표현이다.
#   SMTP 사용자명   = 액세스 키 ID 그대로
#   SMTP 비밀번호   = 비밀 액세스 키를 HMAC-SHA256 으로 변환한 값
#
# 콘솔의 "SMTP 자격 증명 생성" 버튼이 이 셋(사용자 생성 · 키 발급 · 변환)을
# 한 번에 하는데, 계정 제공사가 사람 계정에 iam:CreateUser 를 명시적으로
# 거부해 두어 그 버튼이 막힌다. 거부는 SCP 가 아니라 devcos-team02 사용자에
# 붙은 identity-based policy 라, 별개 주체인 tf-apply 역할에는 걸리지 않을
# 수 있다 — 이 파일의 apply 가 그것을 판정한다 (#320).
#
# 여기서 액세스 키는 만들지 않는다. aws_iam_access_key 는 비밀키를 tfstate 에
# 평문으로 남기고, 그러면 읽기 전용이어야 할 tf-plan 역할까지 발송 자격을
# 갖게 된다. 키는 사람이 IAM 콘솔에서 발급한다.
#
# 설계 근거: mail.md §4.2
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_iam_user" "smtp" {
  # checkov:skip=CKV_AWS_273: SES SMTP 인증은 IAM 사용자로만 가능하다. SMTP 는
  # 사용자명·비밀번호로 인증하는 프로토콜이라 SSO·역할로 대체할 수단이 없다.
  # 대체안(SES API + 인스턴스 역할)은 IMDS hop limit 을 2로 올려 컨테이너
  # 9개 전부에 인스턴스 자격을 여는 대가가 따른다 — mail.md §3 참조.
  name = "${var.project}-ses-smtp"

  # 키를 사람이 콘솔에서 만들기 때문에 terraform 은 그 키의 존재를 모른다.
  # 이게 없으면 나중에 destroy 가 "키가 남아 있다"로 실패한다.
  force_destroy = true

  tags = {
    Component = "mail"
  }
}

# 콘솔이 자동 생성하던 정책은 Resource 가 "*" 였다. 좁힌다 —
# 키가 새더라도 우리 도메인 이름으로만 보낼 수 있게 한다.
data "aws_iam_policy_document" "smtp_send" {
  statement {
    sid    = "SendFromOwnDomainOnly"
    effect = "Allow"

    # SMTP 인터페이스는 SendRawEmail 로 매핑된다. SendEmail(API)은 지금
    # 쓰지 않으므로 넣지 않는다 — 나중에 API 로 옮기면 그때 추가한다.
    actions = ["ses:SendRawEmail"]

    resources = [aws_sesv2_email_identity.domain.arn]

    condition {
      test     = "StringLike"
      variable = "ses:FromAddress"
      values   = ["*@${var.domain}"]
    }
  }
}

# 정책을 사용자에 직접 붙이지 않고 그룹을 거친다.
#
# 사용자 하나에 그룹 하나가 과해 보이지만, SES 콘솔이 자동 생성하던 구성도
# 같은 모양이었다(AWSSESSendingGroupDoNotRename). 권한이 사용자에 흩어지면
# 누가 무엇을 할 수 있는지 세는 방법이 사람마다 달라진다.
resource "aws_iam_group" "smtp" {
  name = "${var.project}-ses-smtp"
}

resource "aws_iam_group_policy" "smtp_send" {
  name   = "ses-send"
  group  = aws_iam_group.smtp.name
  policy = data.aws_iam_policy_document.smtp_send.json
}

resource "aws_iam_user_group_membership" "smtp" {
  user   = aws_iam_user.smtp.name
  groups = [aws_iam_group.smtp.name]
}
