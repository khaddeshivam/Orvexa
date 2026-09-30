package com.orvexa.controlplane.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record SessionEventPageResponse(
        List<SessionEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static SessionEventPageResponse from(Page<SessionEventResponse> page) {
        return new SessionEventPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
