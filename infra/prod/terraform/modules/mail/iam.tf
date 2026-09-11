# ─────────────────────────────────────────────────────────────────────────────
# 인스턴스 역할이 SES 로 보낼 권한
#
# SMTP 가 아니라 API 로 보낸다. SMTP 는 사용자명·비밀번호로 인증하는데, 그
# 비밀번호는 IAM 액세스 키에서 파생되고 이 계정은 키 생성을 거부한다
# (#320 · iam:CreateUser 와 iam:CreateAccessKey 둘 다 explicit deny).
#
# API 는 임시 자격으로 인증한다. 그래서 장기 키가 없다 — 계정 제공사가
# 막으려던 바로 그것이 없어진다.
#
# 정책을 mail 모듈이 만들고 compute 가 붙인다. storage 와 같은 모양이다 —
# 자격(identity)을 아는 쪽이 그 자격에 대한 정책도 갖는다.
#
# 설계 근거: mail.md §3.1 · 이슈 #346
# ─────────────────────────────────────────────────────────────────────────────

data "aws_iam_policy_document" "send" {
  statement {
    sid    = "SendFromOwnDomainOnly"
    effect = "Allow"

    # SESv2 SendEmail 하나면 된다. SendRawEmail(SMTP 경로)은 쓰지 않는다.
    actions = ["ses:SendEmail"]

    # 자격을 특정한다. 계정에 다른 팀의 자격이 생겨도 그쪽으로는 못 보낸다.
    resources = [aws_sesv2_email_identity.domain.arn]

    # 리소스를 좁혀도 From 주소까지 강제되지는 않는다. 조건을 따로 건다.
    condition {
      test     = "StringLike"
      variable = "ses:FromAddress"
      values   = ["*@${var.domain}"]
    }
  }
}

resource "aws_iam_policy" "send" {
  name        = "${var.project}-instance-ses-send"
  description = "EC2 instance profile: send mail through SES from our own domain only."
  policy      = data.aws_iam_policy_document.send.json

  tags = {
    Component = "mail"
  }
}
