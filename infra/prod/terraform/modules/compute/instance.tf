# ─────────────────────────────────────────────────────────────────────────────
# EC2 · EBS · EIP
#
# 설계 근거: network-compute.md §3
# ─────────────────────────────────────────────────────────────────────────────

# AMI 를 하드코딩하지 않는다. 하드코딩하면 보안 패치를 추적할 수 없다.
data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_instance" "main" {
  ami           = data.aws_ssm_parameter.al2023_arm64.value
  instance_type = var.instance_type

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

    # hop limit 1 이면 도커 컨테이너가 IMDS 에 닿지 못한다. 이게 의도다 —
    # AWS 를 호출하는 것은 호스트의 deploy.sh · backup.sh 뿐이다.
    # 컨테이너가 자격증명이 필요해지면 그때 2 로 올린다.
    http_put_response_hop_limit = 1
  }

  user_data = templatefile("${path.module}/user_data.sh", {
    app_dir              = var.app_dir
    config_bucket        = var.config_bucket
    ssm_parameter_prefix = var.ssm_parameter_prefix
    aws_region           = var.aws_region
    swap_size_gb         = var.swap_size_gb
  })

  # user_data 를 고쳐도 인스턴스를 재생성하지 않는다(기본값 false).
  # 설정 변경은 S3 재동기화 + SSM 으로 반영한다 — 서버를 갈아엎을 일이 아니다.

  lifecycle {
    # SSM 파라미터가 최신 AMI 를 가리키므로, AWS 가 새 AMI 를 낼 때마다
    # terraform 이 인스턴스 교체를 계획한다. 배포 때마다 서버가 재생성되면 곤란하다.
    # 의도적으로 AMI 를 올릴 때만 이 줄을 잠시 빼거나 -replace 로 처리한다.
    ignore_changes = [ami]
  }

  tags = {
    Name = "${var.project}-prod"
    # cost-guard 의 일일 잡이 이 태그로 정지 대상을 찾는다.
    # 이름을 바꾸면 백스톱이 조용히 아무것도 못 멈추게 된다.
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
