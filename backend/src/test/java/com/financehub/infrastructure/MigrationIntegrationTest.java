package com.financehub.infrastructure;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Requires Docker; failure to access Docker must fail verification rather than silently skip. */
@Testcontainers
class MigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @BeforeAll static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").load().migrate();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test void storesAllRolesAndReappliesMigrationsWithoutChanges() throws SQLException {
        try (var c = connection(); var statement = c.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM roles")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(6);
        }
        assertThat(Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load().migrate().migrationsExecuted).isZero();
    }

    @Test void auditRecordsCannotBeUpdatedOrDeleted() throws SQLException {
        try (var c = connection(); var statement = c.createStatement()) {
            statement.executeUpdate("INSERT INTO audit_logs(id, action, resource_type) VALUES ('00000000-0000-0000-0000-000000000001', 'TEST', 'TEST')");
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE audit_logs SET action='MODIFIED'"))
                .isInstanceOf(SQLException.class).hasMessageContaining("immutable");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM audit_logs"))
                .isInstanceOf(SQLException.class).hasMessageContaining("immutable");
        }
    }

    @Test void membershipRequiresExistingWorkspaceAndUser() throws SQLException {
        try (var c = connection(); var statement = c.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                INSERT INTO workspace_members(workspace_id, user_id, role_code)
                VALUES ('00000000-0000-0000-0000-000000000002',
                        '00000000-0000-0000-0000-000000000003', 'MEMBER')
                """))
                .isInstanceOf(SQLException.class).satisfies(e -> assertThat(((SQLException)e).getSQLState()).isEqualTo("23503"));
        }
    }
}
