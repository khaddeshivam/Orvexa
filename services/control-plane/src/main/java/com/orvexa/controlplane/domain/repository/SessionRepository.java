package com.orvexa.controlplane.domain.repository;

import com.orvexa.controlplane.domain.model.SessionEntity;
import com.orvexa.controlplane.domain.model.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    Page<SessionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<SessionEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId,
            SessionStatus status,
            Pageable pageable
    );

    Optional<SessionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionEntity s where s.id = :id and s.tenantId = :tenantId")
    Optional<SessionEntity> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionEntity s where s.id = :id")
    Optional<SessionEntity> findByIdForUpdate(@Param("id") UUID id);
}
