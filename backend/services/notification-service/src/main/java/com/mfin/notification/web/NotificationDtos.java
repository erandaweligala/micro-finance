package com.mfin.notification.web;

import com.mfin.notification.domain.NotificationLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    @Schema(description = "A sent or attempted notification. Recipients are always masked.")
    public record NotificationResponse(
            UUID id,
            NotificationLog.Channel channel,
            String templateCode,
            @Schema(example = "j***@example.com") String recipientMasked,
            String subject,
            NotificationLog.Status status,
            int attempts,
            String lastError,
            Instant sentAt,
            Instant createdAt
    ) {
        public static NotificationResponse from(NotificationLog entry) {
            return new NotificationResponse(entry.getId(), entry.getChannel(),
                    entry.getTemplateCode(), entry.getRecipientMasked(), entry.getSubject(),
                    entry.getStatus(), entry.getAttempts(), entry.getLastError(),
                    entry.getSentAt(), entry.getCreatedAt());
        }
    }
}
