package com.financehub.identity.presentation;

import com.financehub.identity.application.*;
import com.financehub.identity.domain.IdentityPolicy;
import com.financehub.identity.infrastructure.AuthRateLimiter;
import com.financehub.shared.exceptions.ApiFailure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityService identity;
    private final AuthRateLimiter limits;
    private final Clock clock;
    private final boolean secure;
    public AuthController(IdentityService identity,AuthRateLimiter limits,Clock clock,
                          @Value("${financehub.auth.secure-cookies:true}") boolean secure) {
        this.identity=identity; this.limits=limits; this.clock=clock; this.secure=secure;
    }
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody Registration request,HttpServletRequest servlet) {
        limit(servlet,request.email(),"register",5);
        identity.register(request.email(),request.password(),request.displayName());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody Login request,HttpServletRequest servlet) {
        limit(servlet,request.email(),"login",10);
        return authenticated(identity.login(request.email(),request.password(),request.deviceName()).orElseThrow(ApiFailure::unauthenticated));
    }
    @PostMapping("/refresh")
    ResponseEntity<?> refresh(@CookieValue(name="FH_REFRESH",required=false) String token,HttpServletRequest servlet) {
        limits.check("refresh-ip:"+servlet.getRemoteAddr(),60);
        var result=identity.refresh(token);
        if(result.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.SET_COOKIE,cookie("",Duration.ZERO).toString()).cacheControl(CacheControl.noStore())
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,"Authentication failed"));
        return authenticated(result.get());
    }
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name="FH_REFRESH",required=false) String token,HttpServletRequest servlet) {
        limits.check("logout-ip:"+servlet.getRemoteAddr(),60);
        identity.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,cookie("",Duration.ZERO).toString())
            .cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/verify-email")
    ResponseEntity<Void> verify(@Valid @RequestBody Challenge request,HttpServletRequest servlet) {
        limits.check("verify-ip:"+servlet.getRemoteAddr(),10);
        identity.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgot(@Valid @RequestBody Email request,HttpServletRequest servlet) {
        limit(servlet,request.email(),"forgot",3);
        identity.requestChallenge(request.email(),"RESET_PASSWORD");
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/resend-verification")
    ResponseEntity<Void> resend(@Valid @RequestBody Email request,HttpServletRequest servlet) {
        limit(servlet,request.email(),"verify-request",3);
        identity.requestChallenge(request.email(),"VERIFY_EMAIL");
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/reset-password")
    ResponseEntity<Void> reset(@Valid @RequestBody Reset request,HttpServletRequest servlet) {
        limits.check("reset-ip:"+servlet.getRemoteAddr(),10);
        identity.resetPassword(request.token(),request.password());
        return ResponseEntity.noContent().build();
    }
    private void limit(HttpServletRequest servlet,String email,String purpose,int count) {
        // Do not trust arbitrary X-Forwarded-For headers. Configure a trusted ingress before forwarding addresses.
        limits.check(purpose+"-ip:"+servlet.getRemoteAddr(),count*3);
        limits.check(purpose+"-email:"+IdentityPolicy.normalizeEmail(email),count);
    }
    private ResponseEntity<TokenResponse> authenticated(IdentityService.AuthResult auth) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie(auth.refreshToken(),Duration.between(clock.instant(),auth.sessionExpiresAt())).toString())
            .cacheControl(CacheControl.noStore()).body(new TokenResponse(auth.accessToken(),"Bearer",AccessTokens.LIFETIME.toSeconds()));
    }
    private ResponseCookie cookie(String value,Duration age) {
        return ResponseCookie.from("FH_REFRESH",value).httpOnly(true).secure(secure).sameSite("Strict")
            .path("/api/v1/auth").maxAge(age).build();
    }
    public record Registration(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email,@NotBlank @Size(min=12,max=72) String password,
                               @NotBlank @Size(max=120) String displayName) {}
    public record Login(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email,@NotBlank @Size(max=256) String password,
                        @NotBlank @Size(max=120) String deviceName) {}
    public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email) {}
    public record Challenge(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {}
    public record Reset(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,@NotBlank @Size(min=12,max=72) String password) {}
    public record TokenResponse(String accessToken,String tokenType,long expiresIn) {}
}
