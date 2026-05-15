package com.work.mautonlaundry.dtos.responses.handoffresponses;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class HandoffCodeView {
    private String stage;
    private String code;
    private Instant expiresAt;
    private Instant issuedAt;
}
