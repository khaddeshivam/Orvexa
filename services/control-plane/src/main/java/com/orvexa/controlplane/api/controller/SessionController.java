package com.orvexa.controlplane.api.controller;

import com.orvexa.controlplane.api.dto.CreateSessionRequest;
import com.orvexa.controlplane.api.dto.SessionEventPageResponse;
import com.orvexa.controlplane.api.dto.SessionEventResponse;
import com.orvexa.controlplane.api.dto.SessionPageResponse;
import com.orvexa.controlplane.api.dto.SessionResponse;
import com.orvexa.controlplane.api.dto.TransitionSessionRequest;
import com.orvexa.controlplane.domain.model.SessionStatus;
import com.orvexa.controlplane.domain.service.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-Actor-Id") UUID actorId,
            @Valid @RequestBody CreateSessionRequest request
    ) {
        return SessionResponse.from(sessionService.create(tenantId, actorId, request));
    }

    @GetMapping
    public SessionPageResponse list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return SessionPageResponse.from(
                sessionService.list(tenantId, status, pageable).map(SessionResponse::from)
        );
    }

    @GetMapping("/{sessionId}")
    public SessionResponse get(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId
    ) {
        return SessionResponse.from(sessionService.get(tenantId, sessionId));
    }

    @PatchMapping("/{sessionId}/status")
    public SessionResponse transition(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody TransitionSessionRequest request
    ) {
        return SessionResponse.from(sessionService.transition(tenantId, sessionId, request.status()));
    }

    @GetMapping("/{sessionId}/events")
    public SessionEventPageResponse events(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sequenceNumber"));
        return SessionEventPageResponse.from(
                sessionService.events(tenantId, sessionId, pageable).map(SessionEventResponse::from)
        );
    }
}
