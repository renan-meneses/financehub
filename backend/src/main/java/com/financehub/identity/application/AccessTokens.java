package com.financehub.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class AccessTokens {
    public static final Duration LIFETIME = Duration.ofMinutes(5);
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final String keyId;
    public AccessTokens(JwtEncoder encoder, Clock clock, @Value("${financehub.auth.issuer}") String issuer,
                        @Value("${financehub.auth.key-id}") String keyId) {
        this.encoder = encoder; this.clock = clock; this.issuer = issuer; this.keyId = keyId;
    }
    public String issue(UUID user, UUID session) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(user.toString()).audience(List.of("financehub-api"))
            .issuedAt(now).notBefore(now).expiresAt(now.plus(LIFETIME)).id(UUID.randomUUID().toString())
            .claim("sid", session.toString()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build(), claims)).getTokenValue();
    }
}
