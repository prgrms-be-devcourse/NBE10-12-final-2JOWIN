# ─────────────────────────────────────────────────────────────────────────────
# SES — 발신 전용
#
# 수신은 하지 않는다. MX 를 건드리지 않으므로 no-reply@ 로 보내고 답장은
# 아무 데도 도착하지 않는다 — 본문에 회신 불가를 적는 것은 백엔드 몫이다.
#
# 여기서 만들지 않는 것:
#   SMTP 자격증명 — terraform 이 만들면 tfstate 에 평문으로 남는다. 콘솔에서 발급한다.
#   DNS 레코드     — dnszi 는 terraform 밖이다. 출력의 토큰을 손으로 넣는다.
#
# 설계 근거: mail.md §4 · §5
# ─────────────────────────────────────────────────────────────────────────────

# 반송·불만을 집계할 그릇. 지금은 샌드박스라 반송이 거의 없지만, 설정 세트는
# 만든 뒤에 붙이는 것보다 처음부터 붙여 두는 편이 싸다 — 나중에 붙이면 그
# 전에 보낸 메일의 이력이 비어 있게 된다.
resource "aws_sesv2_configuration_set" "main" {
  configuration_set_name = "${var.project}-default"

  delivery_options {
    # SES 가 수신 서버와 TLS 를 못 맺으면 평문으로 떨어뜨리지 않고 실패시킨다.
    # 본문에 열람 링크의 원문 토큰이 들어간다(mail.md §2).
    tls_policy = "REQUIRE"
  }

  reputation_options {
    reputation_metrics_enabled = true
  }

  sending_options {
    sending_enabled = true
  }

  # 하드 반송·불만 주소를 계정 억제 목록에 넣어 재발송을 막는다.
  #
  # 계정을 여러 팀이 공유하고, 반송률 5% 를 넘기면 계정 전체가 정지된다 —
  # 우리 오타 하나가 남의 메일까지 멈춘다. 억제 목록이 그 재시도를 끊는다.
  suppression_options {
    suppressed_reasons = ["BOUNCE", "COMPLAINT"]
  }

  tags = {
    Component = "mail"
  }
}

# 도메인 자격. 주소 자격과 달리 주소를 늘려도 재인증이 없고, 평판도
# 도메인 단위로 쌓인다.
#
# dkim_signing_attributes 를 비워 두면 Easy DKIM(RSA 2048)이 켜지고
# 토큰 3개가 발급된다. 그 3개를 dnszi 에 CNAME 으로 넣어야 검증이 끝난다.
resource "aws_sesv2_email_identity" "domain" {
  email_identity = var.domain

  # 자격에 기본 설정 세트를 달아 둔다. 이게 있으면 보내는 쪽에서
  # X-SES-CONFIGURATION-SET 헤더를 붙이지 않아도 집계에 잡힌다 —
  # 어댑터가 헤더를 빠뜨려도 추적이 비지 않는다.
  configuration_set_name = aws_sesv2_configuration_set.main.configuration_set_name

  tags = {
    Component = "mail"
  }
}
