# dnszi 에 그대로 옮겨 적을 수 있는 형태로 낸다.
#
# 토큰만 내면 매번 "._domainkey" 를 어디에 붙이는지 다시 찾아봐야 한다.
# 손으로 옮기는 단계라 옮길 것을 완성해서 주는 편이 실수를 줄인다.
output "dkim_dns_records" {
  description = "dnszi 에 등록할 CNAME 3개. 이걸 넣기 전에는 아무것도 보낼 수 없다"
  value = [
    for token in aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens : {
      type  = "CNAME"
      name  = "${token}._domainkey.${var.domain}"
      value = "${token}.dkim.amazonses.com"
    }
  ]
}

output "identity_verification_status" {
  description = "도메인 검증 상태. CNAME 반영 전에는 PENDING 이다"
  value       = aws_sesv2_email_identity.domain.verified_for_sending_status
}

# .env 의 MAIL_SMTP_HOST 값이다. 리전을 바꾸면 이 주소도 바뀌는데,
# 손으로 적어 두면 리전 변경 때 어긋난다.
output "smtp_host" {
  description = "MAIL_SMTP_HOST 에 넣을 값. 포트는 587(STARTTLS)"
  value       = "email-smtp.${var.aws_region}.amazonaws.com"
}

output "configuration_set_name" {
  description = "자격에 기본으로 달린 설정 세트"
  value       = aws_sesv2_configuration_set.main.configuration_set_name
}

# compute 가 인스턴스 역할에 붙인다. storage 의 instance_access_policy_arn 과 같은 쓰임이다.
output "send_policy_arn" {
  description = "인스턴스 역할에 붙일 SES 발송 정책 ARN"
  value       = aws_iam_policy.send.arn
}
