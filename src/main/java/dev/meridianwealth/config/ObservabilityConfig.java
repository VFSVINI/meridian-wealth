package dev.meridianwealth.config;

import dev.meridianwealth.application.port.CacheStore;
import dev.meridianwealth.infrastructure.observability.MeteredCacheStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Observabilidade na borda: todo {@link CacheStore} do contexto (memória ou Redis) ganha contadores de acerto e erro. */
@Configuration
public class ObservabilityConfig {
    @Bean
    static BeanPostProcessor meteredCaches(ObjectProvider<MeterRegistry> registry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof CacheStore cache && !(bean instanceof MeteredCacheStore)) {
                    MeterRegistry r = registry.getIfAvailable();
                    if (r != null) return new MeteredCacheStore(cache, r);
                }
                return bean;
            }
        };
    }
}
