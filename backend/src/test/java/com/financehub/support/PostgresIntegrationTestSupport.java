package com.financehub.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"financehub.mail.enabled=false","spring.profiles.active=test","financehub.auth.secure-cookies=false"})
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17-alpine");
    private static final Path KEYS=keys();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",DB::getJdbcUrl);
        p.add("spring.datasource.username",DB::getUsername);
        p.add("spring.datasource.password",DB::getPassword);
        p.add("financehub.auth.key-directory",()->KEYS.toString());
        p.add("financehub.auth.key-id",()->"test");
        p.add("financehub.auth.issuer",()->"https://test.financehub");
        p.add("financehub.public-url",()->"https://test.financehub");
    }
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @Autowired protected org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @Autowired protected com.financehub.identity.infrastructure.AuthRateLimiter limiter;
    private static Path keys() {
        try {
            var path=Files.createTempDirectory("financehub-test-keys-");
            var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
            var pair=generator.generateKeyPair();
            Files.writeString(path.resolve("test.key"),Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            Files.writeString(path.resolve("test.pub"),Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            path.toFile().deleteOnExit(); path.resolve("test.key").toFile().deleteOnExit(); path.resolve("test.pub").toFile().deleteOnExit();
            return path;
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    protected ResultActions postJson(String path,Object body,Cookie... cookies) throws Exception {
        var csrf=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        var request=post(path).cookie(csrf).header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content(json.writeValueAsString(body));
        if(cookies.length>0) request.cookie(cookies);
        // Distinct transport addresses avoid unrelated tests sharing IP buckets.
        request.with(r -> {r.setRemoteAddr(UUID.randomUUID().toString()); return r;});
        return mvc.perform(request);
    }
    protected String register() throws Exception {
        String email=UUID.randomUUID()+"@example.com";
        postJson("/api/v1/auth/register",Map.of("email",email,"password","correct horse battery","displayName","Alice"))
            .andExpect(status().isAccepted());
        return email;
    }
    protected String challenge(String email) {
        return jdbc.queryForObject("SELECT body FROM email_outbox WHERE recipient=? ORDER BY created_at DESC LIMIT 1",String.class,email)
            .split("token=")[1].split("\\s")[0];
    }
    protected Login login(String email) throws Exception {
        var response=postJson("/api/v1/auth/login",Map.of("email",email,"password","correct horse battery","deviceName","Test browser"))
            .andExpect(status().isOk()).andReturn().getResponse();
        return new Login(json.readTree(response.getContentAsString()).get("accessToken").asText(),response.getCookie("FH_REFRESH"));
    }
    protected Login verifiedUser() throws Exception {
        var email=register();
        postJson("/api/v1/auth/verify-email",Map.of("token",challenge(email))).andExpect(status().isNoContent());
        return login(email);
    }
    protected record Login(String access,Cookie cookie) {}
}
