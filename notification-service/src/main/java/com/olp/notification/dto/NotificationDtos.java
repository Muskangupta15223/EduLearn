package com.olp.notification.dto;

import com.olp.notification.model.NotificationLog;
import java.time.LocalDateTime;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record NotificationResponse(
            Long id,
            Long userId,
            String recipient,
            String title,
            String message,
            String type,
            Boolean isRead,
            LocalDateTime sentAt
    ) {
        public static NotificationResponse from(NotificationLog log) {
            return new NotificationResponse(
                    log.getId(),
                    log.getUserId(),
                    log.getRecipient(),
                    log.getTitle(),
                    log.getMessage(),
                    log.getType(),
                    log.getIsRead(),
                    log.getSentAt()
            );
        }
    }

    public record NotificationCreateRequest(String title, String message, String type) {
    }

    public record BroadcastRequest(String title, String message, String type) {
    }

    public static List<NotificationResponse> toResponses(List<NotificationLog> logs) {
        return logs.stream().map(NotificationResponse::from).toList();
    }
}
