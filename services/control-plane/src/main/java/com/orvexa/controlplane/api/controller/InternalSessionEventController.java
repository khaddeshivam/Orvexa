package com.orvexa.controlplane.api.controller;

import com.orvexa.controlplane.api.dto.AppendSessionEventRequest;
import com.orvexa.controlplane.api.dto.SessionEventResponse;
import com.orvexa.controlplane.domain.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/sessions")
public class InternalSessionEventController {

    private final SessionService sessionService;

    public InternalSessionEventController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/{sessionId}/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SessionEventResponse appendEvent(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AppendSessionEventRequest request,
            @RequestHeader("X-Internal-Caller") String caller
    ) {
        return SessionEventResponse.from(sessionService.appendInternalEvent(sessionId, request));
    }
}
