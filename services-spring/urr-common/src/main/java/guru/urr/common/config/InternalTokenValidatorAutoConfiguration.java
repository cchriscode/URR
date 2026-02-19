package guru.urr.common.config;

import guru.urr.common.security.InternalTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "INTERNAL_API_TOKEN")
public class InternalTokenValidatorAutoConfiguration {

    @Bean
    public InternalTokenValidator internalTokenValidator(
            @Value("${INTERNAL_API_TOKEN}") String token) {
        return new InternalTokenValidator(token);
    }
}
