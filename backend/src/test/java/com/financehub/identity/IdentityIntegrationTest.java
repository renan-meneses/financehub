package com.financehub.identity;

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

class IdentityIntegrationTest extends com.financehub.support.PostgresIntegrationTestSupport {
    @Test void requiresVerificationAndSingleUseChallenge() throws Exception {
        var email=register();
        postJson("/api/v1/auth/login",Map.of("email",email,"password","correct horse battery","deviceName","Test"))
            .andExpect(status().isUnauthorized());
        var token=challenge(email);
        postJson("/api/v1/auth/verify-email",Map.of("token",token)).andExpect(status().isNoContent());
        postJson("/api/v1/auth/verify-email",Map.of("token",token)).andExpect(status().isUnauthorized());
        var session=login(email);
        assertThat(session.cookie().isHttpOnly()).isTrue();
        assertThat(session.cookie().getPath()).isEqualTo("/api/v1/auth");
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+session.access()))
            .andExpect(status().isOk()).andExpect(jsonPath("email").value(email)).andExpect(jsonPath("passwordHash").doesNotExist());
    }
    @Test void refreshReuseRevokesDescendantsAndAccessImmediately() throws Exception {
        var login=verifiedUser();
        var refreshed=postJson("/api/v1/auth/refresh",Map.of(),login.cookie()).andExpect(status().isOk()).andReturn().getResponse();
        var newCookie=refreshed.getCookie("FH_REFRESH");
        var access=json.readTree(refreshed.getContentAsString()).get("accessToken").asText();
        postJson("/api/v1/auth/refresh",Map.of(),login.cookie()).andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/refresh",Map.of(),newCookie).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
    }
    @Test void concurrentRefreshAllowsOneRotationAndRevokesFamilyOnReplay() throws Exception {
        var login=verifiedUser();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Integer> refresh=()->{gate.await();return postJson("/api/v1/auth/refresh",Map.of(),login.cookie()).andReturn().getResponse().getStatus();};
            var first=pool.submit(refresh);var second=pool.submit(refresh);gate.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,401);
        }
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+login.access())).andExpect(status().isUnauthorized());
    }
    @Test void rejectsCsrfAndUnauthenticatedRequests() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/workspaces")).andExpect(status().isUnauthorized());
    }
    @Test void isolatesWorkspacesAndRollsBackInvalidCreation() throws Exception {
        var alice=verifiedUser();var bob=verifiedUser();
        var response=mvc.perform(post("/api/v1/workspaces").header("Authorization","Bearer "+alice.access()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("name","Personal","kind","PERSONAL","currency","BRL","timezone","America/Fortaleza"))))
            .andExpect(status().isCreated()).andReturn().getResponse();
        String id=json.readTree(response.getContentAsString()).get("id").asText();
        mvc.perform(get("/api/v1/workspaces/"+id).header("Authorization","Bearer "+bob.access())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/workspaces").header("Authorization","Bearer "+bob.access()))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
        long before=jdbc.queryForObject("SELECT count(*) FROM workspaces",Long.class);
        mvc.perform(post("/api/v1/workspaces").header("Authorization","Bearer "+alice.access()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("name","Invalid","kind","PERSONAL","currency","BRL","timezone","Invalid/Zone"))))
            .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workspaces",Long.class)).isEqualTo(before);
    }
    @Test void logoutAndSuspensionInvalidateTokens() throws Exception {
        var login=verifiedUser();
        postJson("/api/v1/auth/logout",Map.of(),login.cookie()).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+login.access())).andExpect(status().isUnauthorized());
        var second=verifiedUser();
        var response=mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+second.access())).andReturn().getResponse();
        jdbc.update("UPDATE users SET suspended_at=now() WHERE id=?",UUID.fromString(json.readTree(response.getContentAsString()).get("id").asText()));
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+second.access())).andExpect(status().isUnauthorized());
    }
    @Test void resetRevokesSessionsAndCannotBeReplayed() throws Exception {
        var email=register();
        postJson("/api/v1/auth/verify-email",Map.of("token",challenge(email))).andExpect(status().isNoContent());
        var login=login(email);
        postJson("/api/v1/auth/forgot-password",Map.of("email",email)).andExpect(status().isAccepted());
        var reset=challenge(email);
        postJson("/api/v1/auth/reset-password",Map.of("token",reset,"password","replacement password"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+login.access())).andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/refresh",Map.of(),login.cookie()).andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/reset-password",Map.of("token",reset,"password","another replacement"))
            .andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/login",Map.of("email",email,"password","replacement password","deviceName","Reset browser"))
            .andExpect(status().isOk());
    }
    @Test void preventsStaleProfileUpdatesAndCrossUserRevocation() throws Exception {
        var alice=verifiedUser(); var bob=verifiedUser();
        var profile=mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+alice.access())).andReturn().getResponse();
        long version=json.readTree(profile.getContentAsString()).get("version").asLong();
        var request=Map.of("displayName","Updated name","version",version);
        mvc.perform(patch("/api/v1/users/me").header("Authorization","Bearer "+alice.access()).contentType("application/json")
            .content(json.writeValueAsString(request))).andExpect(status().isOk()).andExpect(jsonPath("displayName").value("Updated name"));
        mvc.perform(patch("/api/v1/users/me").header("Authorization","Bearer "+alice.access()).contentType("application/json")
            .content(json.writeValueAsString(request))).andExpect(status().isConflict());
        var sessions=mvc.perform(get("/api/v1/users/me/sessions").header("Authorization","Bearer "+alice.access())).andReturn().getResponse();
        String sessionId=json.readTree(sessions.getContentAsString()).get(0).get("id").asText();
        mvc.perform(delete("/api/v1/users/me/sessions/"+sessionId).header("Authorization","Bearer "+bob.access())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+alice.access())).andExpect(status().isOk());
    }
    @Test void rejectsIncorrectIssuerAudienceExpiredAndMissingExpiry() throws Exception {
        var login=verifiedUser();
        var legitimate=decoder.decode(login.access());
        for(String invalid:List.of("issuer","audience","expired","no-expiry")) {
            var claims=new HashMap<String,Object>(legitimate.getClaims());
            if(invalid.equals("issuer")) claims.put("iss","https://untrusted.example");
            if(invalid.equals("audience")) claims.put("aud",List.of("different-api"));
            if(invalid.equals("expired")) {
                var expiredAt=java.time.Instant.now().minusSeconds(120);
                claims.put("iat",expiredAt.minusSeconds(300));
                claims.put("exp",expiredAt);
            }
            if(invalid.equals("no-expiry")) claims.remove("exp");
            var token=encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).keyId("test").build(),
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().claims(values->values.putAll(claims)).build())).getTokenValue();
            mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        }
    }
    @Test void rateLimitFailureStillCommitsAttemptCounter() {
        // A minute rollover legitimately resets the counter; retry the observation in that case.
        for(int observation=0;observation<3;observation++) {
            var minute=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            String bucket="test:"+UUID.randomUUID();
            int rejected=0;
            for(int attempt=0;attempt<5;attempt++) {
                try { limiter.check(bucket,3); }
                catch(com.financehub.shared.exceptions.ApiFailure failure) {
                    assertThat(failure.status()).isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
                    rejected++;
                }
            }
            if(!minute.equals(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES))) continue;
            assertThat(rejected).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT attempts FROM auth_rate_limits WHERE key_hash=?",Integer.class,
                com.financehub.identity.domain.IdentityPolicy.hash(bucket))).isEqualTo(5);
            return;
        }
        fail("Could not observe a rate-limit window without a clock rollover");
    }
}
