package com.orvexa.controlplane.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orvexa.controlplane.api.dto.AppendSessionEventRequest;
import com.orvexa.controlplane.api.dto.CreateSessionRequest;
import com.orvexa.controlplane.domain.model.EventActorType;
import com.orvexa.controlplane.domain.model.SessionEventType;
import com.orvexa.controlplane.domain.model.SessionStatus;
import com.orvexa.controlplane.domain.repository.SessionEventRepository;
import com.orvexa.controlplane.domain.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class SessionServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    SessionService sessionService;

    @Autowired
    SessionRepository sessionRepository;

    @Autowired
    SessionEventRepository eventRepository;

    @Autowired
    SessionEventService eventService;

    @Autowired
    ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        sessionRepository.deleteAll();
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
    }

    @Test
    void createsSessionAndDurableCreationEvent() {
        var session = sessionService.create(tenantId, actorId, new CreateSessionRequest(" First support session "));

        assertThat(session.getId()).isNotNull();
        assertThat(session.getTitle()).isEqualTo("First support session");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getLastEventSequence()).isEqualTo(1);

        var events = eventRepository.findBySessionIdOrderBySequenceNumberAsc(
                session.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)
        );
        assertThat(events.getTotalElements()).isEqualTo(1);
        assertThat(events.getContent().getFirst().getEventType()).isEqualTo(SessionEventType.SESSION_CREATED);
    }

    @Test
    void enforcesLifecycleTransitions() {
        var session = sessionService.create(tenantId, actorId, new CreateSessionRequest("Lifecycle"));

        sessionService.transition(tenantId, session.getId(), SessionStatus.ESCALATED);
        var escalated = sessionService.get(tenantId, session.getId());
        assertThat(escalated.getStatus()).isEqualTo(SessionStatus.ESCALATED);

        sessionService.transition(tenantId, session.getId(), SessionStatus.CLOSED);
        var closed = sessionService.get(tenantId, session.getId());
        assertThat(closed.getStatus()).isEqualTo(SessionStatus.CLOSED);

        assertThatThrownBy(() -> sessionService.transition(tenantId, session.getId(), SessionStatus.ACTIVE))
                .isInstanceOf(com.orvexa.controlplane.exception.InvalidStateTransitionException.class);
    }

    @Test
    void replaysSameIdempotencyKeyWithoutCreatingDuplicateEvent() {
        var session = sessionService.create(tenantId, actorId, new CreateSessionRequest("Idempotency"));
        UUID clientEventId = UUID.randomUUID();
        var payload = objectMapper.valueToTree(Map.of("message", "hello"));
        var request = new AppendSessionEventRequest(
                clientEventId,
                SessionEventType.MESSAGE_CREATED,
                EventActorType.USER,
                payload
        );

        var first = sessionService.appendInternalEvent(session.getId(), request);
        var second = sessionService.appendInternalEvent(session.getId(), request);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(eventRepository.findBySessionIdOrderBySequenceNumberAsc(
                session.getId(), org.springframework.data.domain.PageRequest.of(0, 20)
        ).getTotalElements()).isEqualTo(2);
    }
}
