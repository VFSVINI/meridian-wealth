# Descreve o ambiente em produção do MeridianWealth: banco no Neon e serviço web no Render, espelhando o render.yaml.
# ESTE ARQUIVO NÃO É APLICADO pela CI nem por este projeto: serve de documentação executável e foi validado com `terraform validate`.
# Antes de um `terraform apply` leia infra/terraform/README.md (importar o que já existe, para não duplicar serviço nem banco).

resource "neon_project" "db" {
  name           = "meridianwealth"
  pg_version     = 16
  region_id      = "aws-us-east-2"
  store_password = "yes"
}

# segredo de assinatura dos tokens (o render.yaml usa generateValue; aqui o valor nasce no estado do Terraform)
resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}

resource "render_web_service" "app" {
  name              = "meridianwealth"
  plan              = var.render_plan
  region            = "ohio"
  health_check_path = "/actuator/health/liveness"

  runtime_source = {
    docker = {
      repo_url    = var.repo_url
      branch      = var.branch
      auto_deploy = false
    }
  }

  env_vars = merge(
    {
      JAVA_TOOL_OPTIONS     = { value = "-Xmx280m -XX:MaxMetaspaceSize=120m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:+UseSerialGC" }
      MW_PERSISTENCE        = { value = "true" }
      MW_EXCLUDE_AUTOCONFIG = { value = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration" }
      DB_POOL_SIZE          = { value = "5" }
      DB_URL                = { value = "jdbc:postgresql://${neon_project.db.database_host}/${neon_project.db.database_name}?sslmode=require" }
      DB_USER               = { value = neon_project.db.database_user }
      DB_PASSWORD           = { value = neon_project.db.database_password }
      MW_JWT_SECRET         = { value = random_password.jwt_secret.result }
    },
    var.trusted_proxies == "" ? {} : { MW_TRUSTED_PROXIES = { value = var.trusted_proxies } }
  )
}
