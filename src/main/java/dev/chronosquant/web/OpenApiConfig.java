package dev.chronosquant.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "ChronosQuant API", version = "0.2.0",
        description = "Cockpit analítico de timing e risco. Não executa ordens e não é recomendação financeira."))
public class OpenApiConfig {
}
