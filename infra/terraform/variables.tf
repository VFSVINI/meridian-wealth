variable "render_api_key" {
  description = "Chave da API do Render (Account Settings > API Keys). Nunca vai para o Git: use TF_VAR_render_api_key."
  type        = string
  sensitive   = true
}

variable "render_owner_id" {
  description = "ID do dono do serviço no Render (usuário ou time), visível na URL do painel."
  type        = string
}

variable "neon_api_key" {
  description = "Chave da API do Neon (Account settings > API keys). Nunca vai para o Git: use TF_VAR_neon_api_key."
  type        = string
  sensitive   = true
}

variable "repo_url" {
  description = "Repositório que o Render constrói pelo Dockerfile."
  type        = string
  default     = "https://github.com/VFSVINI/meridian-wealth"
}

variable "branch" {
  description = "Branch que o Render constrói."
  type        = string
  default     = "main"
}

variable "render_plan" {
  description = "Plano do serviço web. O projeto roda no plano gratuito; confira se a API do Render aceita criar esse plano antes do primeiro apply."
  type        = string
  default     = "free"
}

variable "trusted_proxies" {
  description = "Faixas de IP do proxy do Render que podem informar o IP real do visitante (MW_TRUSTED_PROXIES); vazio deixa o padrão da aplicação."
  type        = string
  default     = ""
}
