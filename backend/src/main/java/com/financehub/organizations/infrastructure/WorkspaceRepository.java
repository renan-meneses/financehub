package com.financehub.organizations.infrastructure;

import com.financehub.organizations.domain.Workspace;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace,UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT w FROM Workspace w WHERE w.id=:id AND w.archivedAt IS NULL")
    java.util.Optional<Workspace> lockActiveById(@org.springframework.data.repository.query.Param("id") UUID id);
}
