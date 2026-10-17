package com.financehub.identity.infrastructure;

import com.financehub.identity.domain.IdentityPolicy;
import com.financehub.shared.exceptions.ApiFailure;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Database-backed counters survive replicas and commit even when authentication fails. */
@Component
public class AuthRateLimiter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public AuthRateLimiter(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiFailure.class)
    public void check(String bucket, int limit) {
        var start = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Integer count = jdbc.queryForObject("""
            INSERT INTO auth_rate_limits(key_hash, window_start, attempts) VALUES (?, ?, 1)
            ON CONFLICT (key_hash) DO UPDATE SET
            attempts = CASE WHEN auth_rate_limits.window_start = EXCLUDED.window_start
                            THEN auth_rate_limits.attempts + 1 ELSE 1 END,
            window_start = EXCLUDED.window_start RETURNING attempts
            """, Integer.class, IdentityPolicy.hash(bucket), Timestamp.from(start));
        if (count != null && count > limit)
            throw new ApiFailure(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many attempts; retry in one minute");
    }
    @Scheduled(fixedDelay = 3600000)
    public void cleanup() {
        jdbc.update("DELETE FROM auth_rate_limits WHERE window_start < ?", Timestamp.from(clock.instant().minus(Duration.ofHours(2))));
    }
}
