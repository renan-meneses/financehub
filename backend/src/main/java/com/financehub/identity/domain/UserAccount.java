package com.financehub.identity.domain;
import java.time.Instant;
import java.util.UUID;
public record UserAccount(UUID id, String email, String passwordHash, String displayName,
                          Instant verifiedAt, Instant suspendedAt, long version) {
    public boolean active() { return suspendedAt == null && verifiedAt != null; }
}
