package com.financehub.shared.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean
    JWKSet signingKeys(@Value("${financehub.auth.key-directory}") String directory,
                       @Value("${financehub.auth.key-id}") String activeId) throws Exception {
        if (!activeId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid signing key ID");
        var factory = KeyFactory.getInstance("RSA");
        var keys = new ArrayList<com.nimbusds.jose.jwk.JWK>();
        try (var paths = Files.list(Path.of(directory))) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".pub")).toList()) {
                String id = file.getFileName().toString().replaceFirst("\\.pub$", "");
                var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(file)));
                if (publicKey.getModulus().bitLength() < 2048) throw new IllegalArgumentException("RSA key too small");
                var builder = new RSAKey.Builder(publicKey).keyID(id).algorithm(JWSAlgorithm.RS256);
                if (id.equals(activeId)) {
                    var privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pem(Path.of(directory, id + ".key"))));
                    if (!privateKey.getModulus().equals(publicKey.getModulus())) throw new IllegalArgumentException("Key mismatch");
                    builder.privateKey(privateKey);
                }
                keys.add(builder.build());
            }
        }
        if (keys.stream().noneMatch(k -> k.getKeyID().equals(activeId) && k.isPrivate()))
            throw new IllegalArgumentException("Active signing key missing");
        return new JWKSet(keys);
    }
    private byte[] pem(Path path) throws Exception {
        String encoded = Files.readString(path).replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(encoded);
    }
    @Bean JwtEncoder jwtEncoder(JWKSet keys) { return new NimbusJwtEncoder(new ImmutableJWKSet<>(keys)); }
    @Bean
    JwtDecoder jwtDecoder(JWKSet keys, @Value("${financehub.auth.issuer}") String issuer) {
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(keys.toPublicJWKSet())));
        var decoder = new NimbusJwtDecoder(processor);
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains("financehub-api")
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
            && jwt.getNotBefore() != null && jwt.getSubject() != null && jwt.getClaimAsString("sid") != null
            && jwt.getHeaders().get("kid") != null
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required claims missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audience, requiredClaims));
        return decoder;
    }
}
