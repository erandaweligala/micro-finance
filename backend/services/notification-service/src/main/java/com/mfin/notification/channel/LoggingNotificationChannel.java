package com.mfin.notification.channel;

import com.mfin.notification.domain.NotificationLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development transport: records that a message would have been sent, without sending it.
 *
 * <p>Active only when {@code mfin.notifications.provider=logging}. Production wires an SMTP or
 * SMS-aggregator implementation of {@link NotificationChannel} instead - nothing else changes.</p>
 */
@Component
@ConditionalOnProperty(name = "mfin.notifications.provider", havingValue = "logging",
        matchIfMissing = true)
public class LoggingNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationChannel.class);

    @Override
    public NotificationLog.Channel channel() {
        // Stands in for every channel in local development.
        return NotificationLog.Channel.EMAIL;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        // The address is masked even here: development logs are still logs.
        log.info("[notification] to={} subject={} bodyLength={}", mask(recipient), subject,
                body == null ? 0 : body.length());
    }

    private String mask(String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "****";
        }
        int at = recipient.indexOf('@');
        if (at > 1) {
            return recipient.charAt(0) + "***" + recipient.substring(at);
        }
        return "****" + recipient.substring(recipient.length() - 3);
    }
}
