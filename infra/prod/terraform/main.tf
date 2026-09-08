# prod 스택이 호출하는 모듈들.
#
#   network  -> 서브넷 · 보안그룹
#   storage  -> ECR · S3 · 인스턴스 접근 정책
#   compute  -> EC2 · EIP · 인스턴스 프로파일

module "network" {
  source = "./modules/network"

  project           = var.project
  vpc_cidr          = var.vpc_cidr
  public_subnets    = var.public_subnets
  primary_az_suffix = var.primary_az_suffix
}

module "storage" {
  source = "./modules/storage"

  project               = var.project
  ecr_repository_name   = var.ecr_repository_name
  image_retention_count = var.image_retention_count
  bucket_name_prefix    = var.bucket_name_prefix
  backup_retention_days = var.backup_retention_days

  # 루트가 infra/prod/terraform 이므로 한 단계 위가 infra/prod 다.
  # compose · caddy · scripts · monitoring 이 그 아래에 있다.
  config_source_root = "${path.root}/.."
}

# compute 는 network·storage 뒤에 온다.
#   서브넷·보안그룹 -> network
#   설정 번들 버킷 · ECR pull 정책 -> storage
module "compute" {
  source = "./modules/compute"

  # 보안그룹 "규칙"이 다 붙은 뒤에 인스턴스를 만든다.
  #
  # 이게 없으면 terraform 이 둘을 형제로 본다. 인스턴스가 참조하는 것은
  # aws_security_group.web 하나뿐이고, 규칙들은 별도 리소스라 인스턴스와
  # 순서 관계가 없다 — 병렬로 만들어질 수 있다.
  #
  # 아웃바운드 규칙보다 인스턴스가 먼저 뜨면 cloud-init 이 인터넷에
  # 나가지 못해 도커 설치에서 멈춘다. 첫 apply 때 실제로 그랬다
  # (규칙 생성이 실패했는데 인스턴스는 그대로 떴다).
  #
  # 경쟁 조건이라 될 때도 있고 안 될 때도 된다. network 는 작고 빨라서
  # 전체를 기다려도 손해가 없다.
  depends_on = [module.network]

  project             = var.project
  aws_region          = var.aws_region
  instance_type       = var.instance_type
  root_volume_size_gb = var.root_volume_size_gb
  swap_size_gb        = var.swap_size_gb

  subnet_id         = module.network.primary_subnet_id
  security_group_id = module.network.web_security_group_id

  config_bucket      = module.storage.bucket_name
  storage_policy_arn = module.storage.instance_access_policy_arn

  key_name          = var.key_name
  deploy_public_key = var.deploy_public_key
}
