package com.financehub.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {
    @Test
    void loadsApplicationYamlWithoutDuplicateKeysOrObsoleteExternalIssuerSettings() throws Exception {
        var sources = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"));

        assertThat(sources).hasSize(1);
        var configuration = sources.getFirst();
        assertThat(configuration.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(configuration.getProperty("financehub.auth.key-directory"))
            .isEqualTo("${JWT_KEY_DIRECTORY:/keys}");
        assertThat(configuration.getProperty("server.jwt.jwk-set-uri")).isNull();
        assertThat(configuration.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri")).isNull();
    }
}
