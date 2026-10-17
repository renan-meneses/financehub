package com.financehub.shared.security;

import com.financehub.identity.infrastructure.IdentityStore;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,IdentityStore users,Clock clock,SecurityProblems problems,
                                    org.springframework.core.env.Environment environment,
                                    @Value("${springdoc.api-docs.enabled:false}") boolean openApi,
                                    @Value("${financehub.auth.secure-cookies:true}") boolean secureCookies) throws Exception {
        if (!secureCookies && !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local", "test")))
            throw new IllegalStateException("Insecure cookies are restricted to local/test profiles");
        var csrf=new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(c -> c.httpOnly(false).secure(secureCookies).sameSite("Strict").path("/"));
        return http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .logout(c -> c.disable())
            .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(request -> request.getRequestURI().startsWith(request.getContextPath()+"/api/v1/auth/")
                    && !List.of("GET","HEAD","OPTIONS").contains(request.getMethod())))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET,"/actuator/health","/actuator/health/**","/api/v1/auth/csrf").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                        openApi && environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))))
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/register","/api/v1/auth/login","/api/v1/auth/refresh",
                    "/api/v1/auth/logout","/api/v1/auth/verify-email","/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password","/api/v1/auth/resend-verification").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> problems.write(req,res,401,"Authentication required"))
                .accessDeniedHandler((req,res,ex) -> problems.write(req,res,403,"Access denied")))
            .oauth2ResourceServer(o -> o
                .authenticationEntryPoint((req,res,ex) -> problems.write(req,res,401,"Authentication failed"))
                .accessDeniedHandler((req,res,ex) -> problems.write(req,res,403,"Access denied"))
                .jwt(j -> j.jwtAuthenticationConverter(jwt -> {
                    try {
                        UUID user=UUID.fromString(jwt.getSubject());
                        UUID session=UUID.fromString(jwt.getClaimAsString("sid"));
                        if(!users.sessionActive(user,session,clock.instant())) throw new IllegalArgumentException();
                        return new JwtAuthenticationToken(jwt,List.of(),user.toString());
                    } catch(IllegalArgumentException | NullPointerException invalid) {
                        throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
                    }
                })))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .build();
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
