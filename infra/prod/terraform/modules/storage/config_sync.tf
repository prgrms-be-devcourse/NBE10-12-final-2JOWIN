# ─────────────────────────────────────────────────────────────────────────────
# 설정 번들 업로드
#
# compose · Caddyfile · scripts · monitoring · systemd 를 S3 config/ 로 그대로 올린다.
# 서버의 cloud-init 과 deploy.sh 가 여기서 받아간다.
#
# etag 가 바뀌면 terraform 이 그 파일만 다시 올린다. 설정만 고치고 apply 하면
# 인스턴스 재생성 없이 S3 만 갱신되고, 이후 배포 스크립트가 재동기화한다.
#
# 원본은 git 이다. S3 는 서버가 받아갈 수 있게 둔 사본일 뿐이다.
#
# 설계 근거: storage.md §6
# ─────────────────────────────────────────────────────────────────────────────

locals {
  # 아직 비어 있는 디렉터리는 그냥 0개를 만든다 — #92 에서 파일이 들어오면
  # 워크플로를 고치지 않아도 자동으로 올라간다.
  config_files = merge([
    for dir in var.config_dirs : {
      for file in fileset("${var.config_source_root}/${dir}", "**") :
      "${dir}/${file}" => "${var.config_source_root}/${dir}/${file}"
      # .gitkeep 같은 자리표시자는 올리지 않는다
      if !startswith(basename(file), ".")
    }
  ]...)
}

resource "aws_s3_object" "config" {
  for_each = local.config_files

  bucket = aws_s3_bucket.main.id
  key    = "config/${each.key}"
  source = each.value
  etag   = filemd5(each.value)

  # 버킷 기본 암호화가 SSE-S3 라 객체도 그대로 따라간다.
  depends_on = [aws_s3_bucket_server_side_encryption_configuration.main]
}
