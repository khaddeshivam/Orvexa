package com.orvexa.controlplane.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orvexa.controlplane.api.dto.CreateSessionRequest;
import com.orvexa.controlplane.domain.model.SessionEntity;
import com.orvexa.controlplane.domain.model.SessionStatus;
import com.orvexa.controlplane.domain.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionControllerUnitTest {

    private SessionService sessionService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(new SessionController(sessionService)).build();
    }

    @Test
    void createsSessionWithRequiredHeaders() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        SessionEntity entity = new SessionEntity(sessionId, tenantId, actorId, "Support");
        when(sessionService.create(eq(tenantId), eq(actorId), any(CreateSessionRequest.class))).thenReturn(entity);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/sessions")
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Actor-Id", actorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Support\"}"))
                .andExpect(status().isCreated());

        verify(sessionService).create(eq(tenantId), eq(actorId), any(CreateSessionRequest.class));
    }

    @Test
    void rejectsOversizedTitle() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String oversized = "x".repeat(121);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/sessions")
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Actor-Id", actorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSessionRequest(oversized))))
                .andExpect(status().isBadRequest());
    }
}
