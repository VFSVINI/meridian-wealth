package dev.meridianwealth.config;

import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.WebRequest;

/**
 * Corpo de erro padrão do Spring (404, 405, 400 sem tratador): só {@code status} e {@code error}. O {@code path} devolvia a URL pedida
 * (entrada do usuário, decodificada) e o {@code timestamp} não ajuda ninguém: erro que não repete o que o atacante mandou nem dá pista
 * da estrutura interna.
 */
@Configuration
public class ErrorBodyConfig {
    @Bean
    DefaultErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes() {
            @Override
            public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
                Map<String, Object> a = super.getErrorAttributes(request, options);
                a.remove("path");
                a.remove("timestamp");
                return a;
            }
        };
    }
}
