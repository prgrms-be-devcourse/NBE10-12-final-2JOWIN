# ─────────────────────────────────────────────────────────────────────────────
# EC2 · EBS · EIP
#
# 설계 근거: network-compute.md §3
# ─────────────────────────────────────────────────────────────────────────────

# AMI 를 하드코딩하지 않는다. 하드코딩하면 보안 패치를 추적할 수 없다.
data "aws_ssm_parameter" "al2023_x86_64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_instance" "main" {
  ami           = data.aws_ssm_parameter.al2023_x86_64.value
  instance_type = var.instance_type

  key_name = var.key_name

  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  # 프로젝트가 끝나면 destroy 로 0원을 만들어야 한다. 종료 방어를 켜지 않는다.
  disable_api_termination = false

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_size_gb
    encrypted   = true # AWS 관리 키(aws/ebs) — 무료다

    # false 로 두면 인스턴스를 지워도 볼륨이 남아 계속 과금된다.
    delete_on_termination = true

    tags = {
      Name = "${var.project}-root"
    }
  }

  # IMDSv2 강제. IMDSv1 은 SSRF 한 방으로 인스턴스 자격증명이 새어나간다.
  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"

    # 2 로 올린 것은 컨테이너가 자격증명을 필요로 하게 됐기 때문이다.
    # 백엔드가 SES API 로 메일을 보내는데, SMTP 는 이 계정에서 쓸 수 없다 —
    # 인증에 필요한 IAM 액세스 키 생성이 거부된다 (#320 · #346).
    #
    # 대가가 분명하다. hop 2 는 backend 만 여는 게 아니라 컨테이너 9개
    # 전부를 연다. 컨테이너별로 나눌 수 있는 설정이 아니다. 그 안에는
    # Grafana · Loki 같은 외부 이미지도 있다.
    #
    # 그래도 이쪽을 택한 이유는 대안이 장기 키인데 그것을 만들 수 없고,
    # 만들 수 있더라도 임시 자격이 "그 서버에서만 · 몇 시간" 인 반면
    # 정적 키는 "어디서든 · 교체할 때까지" 이기 때문이다.
    #
    # 이 값을 되돌리면 메일 발송이 조용히 전부 실패한다.
    http_put_response_hop_limit = 2
  }

  user_data = templatefile("${path.module}/user_data.sh", {
    app_dir           = var.app_dir
    config_bucket     = var.config_bucket
    aws_region        = var.aws_region
    swap_size_gb      = var.swap_size_gb
    deploy_public_key = var.deploy_public_key
  })

  # user_data 를 고쳐도 인스턴스를 재생성하지 않는다(기본값 false).
  # 설정 변경은 S3 재동기화 + 배포 스크립트로 반영한다 — 서버를 갈아엎을 일이 아니다.

  lifecycle {
    # SSM 파라미터가 최신 AMI 를 가리키므로, AWS 가 새 AMI 를 낼 때마다
    # terraform 이 인스턴스 교체를 계획한다. 배포 때마다 서버가 재생성되면 곤란하다.
    # 의도적으로 AMI 를 올릴 때만 이 줄을 잠시 빼거나 -replace 로 처리한다.
    ignore_changes = [ami]
  }

  tags = {
    # 컴포넌트는 역할로 적는다. "prod" 는 환경이지 컴포넌트가 아니다 —
    # 계정을 여러 팀이 공유하므로 콘솔에서 무엇을 하는 서버인지 보여야 한다.
    Name = "${var.project}-api"
    # 계정을 여러 팀이 공유한다. 이 태그로 Cost Explorer 에서 팀별 비용이
    # 분해되고 콘솔 필터링이 된다.
    Project = var.project
  }
}

# 고정 IP 가 필요한 이유는 하나다 — dnszi 에 A 레코드를 한 번만 등록하려고.
# 기동 시 자동 할당된 공인 IP 는 EIP 연결과 함께 회수되므로 이중 과금은 없다.
resource "aws_eip" "main" {
  domain = "vpc"

  tags = {
    Name = "${var.project}-eip"
  }
}

resource "aws_eip_association" "main" {
  instance_id   = aws_instance.main.id
  allocation_id = aws_eip.main.id
}
