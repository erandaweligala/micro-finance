package com.mfin.notification.channel;

import com.mfin.notification.domain.NotificationLog;

/**
 * A delivery transport. Implementations wrap a real provider (SMTP, an SMS aggregator, FCM);
 * the service layer does not care which, so a tenant can be moved between providers without
 * touching any business logic.
 */
public interface NotificationChannel {

    NotificationLog.Channel channel();

    /**
     * Delivers the message.
     *
     * @param recipient the real address - never logged in full by implementations
     * @throws RuntimeException if delivery fails, so the caller can retry
     */
    void send(String recipient, String subject, String body);
}
