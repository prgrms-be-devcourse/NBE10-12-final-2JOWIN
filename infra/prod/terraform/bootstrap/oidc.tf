# GitHub Actions 가 장기 액세스 키 없이 AWS 역할을 맡을 수 있게 하는 신뢰 앵커.
#
# 만들지 않고 참조만 한다. 이 계정은 여러 팀이 공유하고, 같은 issuer 의
# 공급자는 계정당 하나만 존재할 수 있다. 이미 다른 팀이 만들어 두었고,
# 우리가 소유하면 terraform destroy 한 번에 다른 팀들의 OIDC 가 전부 끊긴다.
#
# 참조만 하면 그 사고가 구조적으로 불가능해진다. 대신 이 공급자가 사라지면
# 우리 배포도 멈추는데, 그건 계정 공유의 대가이고 여기서 감수한다.
#
# 확인한 것 (2026-09-08): client_id_list 가 sts.amazonaws.com 하나로
# 우리 요구와 일치한다. 지문은 다르지만 AWS 는 2023년부터 이 issuer 의
# 지문을 검증하지 않는다.
data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}
