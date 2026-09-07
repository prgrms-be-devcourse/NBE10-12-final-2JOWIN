# GitHub Actions 가 장기 액세스 키 없이 AWS 역할을 맡을 수 있게 하는 신뢰 앵커.
# 계정당 같은 issuer 는 1개만 존재할 수 있다.

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  # AWS 는 2023년부터 이 issuer 의 지문을 검증하지 않는다.
  # 프로바이더가 값 자체는 요구하므로 GitHub 루트 CA 지문을 형식상 채운다.
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}
