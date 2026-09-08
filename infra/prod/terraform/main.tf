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
