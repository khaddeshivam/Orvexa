package com.orvexa.controlplane.domain.repository;

import com.orvexa.controlplane.domain.model.SessionEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionEventRepository extends JpaRepository<SessionEventEntity, UUID> {

    Optional<SessionEventEntity> findBySessionIdAndClientEventId(UUID sessionId, UUID clientEventId);

    Page<SessionEventEntity> findBySessionIdOrderBySequenceNumberAsc(UUID sessionId, Pageable pageable);
}
