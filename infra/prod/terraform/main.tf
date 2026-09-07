# prod 스택이 호출하는 모듈들.
#
# cost-guard 를 가장 먼저 둔다 — 뒤에 올 network·compute·storage 가
# 실제로 돈이 드는 리소스를 만들기 때문에, 그걸 막을 장치가 먼저 있어야 한다.

module "cost_guard" {
  source = "./modules/cost-guard"

  providers = {
    aws = aws
  }

  project                  = var.project
  aws_region               = var.aws_region
  budget_limit_usd         = var.budget_limit_usd
  warn_thresholds          = var.warn_thresholds
  freeze_threshold         = var.freeze_threshold
  stop_threshold           = var.stop_threshold
  stop_instance_ids        = var.stop_instance_ids
  enable_budget_actions    = var.enable_budget_actions
  terraform_exec_role_name = var.terraform_exec_role_name
  developer_user_names     = var.developer_user_names
  allowed_instance_types   = var.allowed_instance_types
  allowed_regions          = var.allowed_regions
  max_volume_size_gb       = var.max_volume_size_gb
  discord_webhook_ssm_path = var.discord_webhook_ssm_path
}

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
#
# cost_guard 의 stop_instance_ids 에 compute 출력을 직접 연결하지 않는다.
# 연결하면 terraform 이 compute 를 먼저 만들고 cost-guard 를 나중에 만든다 —
# 가드레일이 비싼 리소스보다 늦게 서게 되어 순서가 뒤집힌다.
# apply 후 출력값을 tfvars 에 적어 2단계로 채운다 (cost-guard.md §6.5).
# 그 사이 구간은 일일 잡 백스톱이 태그로 찾아 덮는다.
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
}
