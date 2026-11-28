output "service_url" {
  description = "Endereço público do serviço (use como a variável RENDER_SERVICE_URL do fluxo de deploy)."
  value       = render_web_service.app.url
}

output "database_host" {
  description = "Host direto do banco no Neon (sem pooler)."
  value       = neon_project.db.database_host
}
