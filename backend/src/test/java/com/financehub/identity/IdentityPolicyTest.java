package com.financehub.identity;
import com.financehub.identity.domain.IdentityPolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class IdentityPolicyTest {
    @Test void normalizesEmailWithStableLocale() {
        assertThat(IdentityPolicy.normalizeEmail("  ALICE@EXAMPLE.COM ")).isEqualTo("alice@example.com");
    }
    @Test void rejectsBcryptTruncationIncludingUnicode() {
        assertThatThrownBy(() -> IdentityPolicy.validatePassword("a".repeat(73))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdentityPolicy.validatePassword("€".repeat(25))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> IdentityPolicy.validatePassword("€".repeat(24))).doesNotThrowAnyException();
    }
    @Test void createsOpaqueTokensAndStoresOnlyFixedLengthHashes() {
        var token=IdentityPolicy.newToken();
        assertThat(IdentityPolicy.validTokenShape(token)).isTrue();
        assertThat(IdentityPolicy.hash(token)).hasSize(64).doesNotContain(token);
        assertThat(IdentityPolicy.newToken()).isNotEqualTo(token);
    }
}
