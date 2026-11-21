package dev.meridianwealth.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "MeridianWealth API", version = "0.1.0",
        description = "MeridianWealth: plataforma analítica multi-ativo de patrimônio e risco. Não executa ordens e não é recomendação financeira."))
public class OpenApiConfig {
}
