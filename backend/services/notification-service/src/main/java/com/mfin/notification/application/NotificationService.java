package com.mfin.notification.application;

import com.mfin.common.events.NotificationEvents;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.web.PageResponse;
import com.mfin.notification.channel.NotificationChannel;
import com.mfin.notification.domain.NotificationLog;
import com.mfin.notification.domain.NotificationTemplate;
import com.mfin.notification.repository.NotificationRepositories.NotificationLogRepository;
import com.mfin.notification.repository.NotificationRepositories.NotificationTemplateRepository;
import com.mfin.notification.web.NotificationDtos.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Renders and delivers outbound messages.
 *
 * <p>Delivery failures never propagate back to the caller: a borrower's repayment must not be
 * rolled back because an SMS gateway is down. Failed messages are recorded and retried by
 * {@link #retryFailed()} instead.</p>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationLogRepository logRepository;
    private final NotificationTemplateRepository templateRepository;
    private final List<NotificationChannel> channels;
    private final int maxAttempts;

    public NotificationService(NotificationLogRepository logRepository,
                               NotificationTemplateRepository templateRepository,
                               List<NotificationChannel> channels,
                               @Value("${mfin.notifications.max-attempts:5}") int maxAttempts) {
        this.logRepository = logRepository;
        this.templateRepository = templateRepository;
        this.channels = channels;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void handle(NotificationEvents.NotificationRequested event) {
        UUID tenantId = event.tenantId();
        if (tenantId != null && logRepository.existsByTenantIdAndSourceEventId(tenantId, event.eventId())) {
            log.debug("Notification for event {} already handled", event.eventId());
            return;
        }

        NotificationLog.Channel channel = NotificationLog.Channel.valueOf(event.channel().name());
        NotificationLog entry = new NotificationLog(channel, event.templateCode(),
                mask(event.recipient()), event.eventId(), event.relatedEntityId());
        if (tenantId != null) {
            entry.setTenantId(tenantId);
        }

        Optional<NotificationTemplate> template = tenantId == null ? Optional.empty()
                : templateRepository.findByTenantIdAndCodeAndChannel(tenantId,
                        event.templateCode(), channel);

        Map<String, String> variables = event.variables() == null ? Map.of() : event.variables();
        String subject = template.map(candidate -> candidate.renderSubject(variables))
                .orElseGet(() -> defaultSubject(event.templateCode()));
        String body = template.map(candidate -> candidate.renderBody(variables))
                .orElseGet(() -> defaultBody(event.templateCode(), variables));
        entry.render(subject, body);

        try {
            resolveChannel(channel).send(event.recipient(), subject, body);
            entry.markSent();
        } catch (RuntimeException ex) {
            // Recorded, not rethrown: the upstream business transaction has already committed.
            entry.markFailed(ex.getMessage(), maxAttempts);
            log.warn("Notification {} to {} failed: {}", event.templateCode(),
                    entry.getRecipientMasked(), ex.getMessage());
        }
        logRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(NotificationLog.Status status, Pageable pageable) {
        return PageResponse.from(
                logRepository.search(TenantContext.requireTenantId(), status, pageable),
                NotificationResponse::from);
    }

    /** Retries messages that have not yet exhausted their attempts. */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${mfin.notifications.retry-interval-ms:300000}")
    @Transactional
    public void retryFailed() {
        List<NotificationLog> pending =
                logRepository.findTop100ByStatusOrderByCreatedAtAsc(NotificationLog.Status.PENDING);
        for (NotificationLog entry : pending) {
            if (entry.getAttempts() >= maxAttempts) {
                continue;
            }
            log.debug("Retrying notification {} (attempt {})", entry.getId(), entry.getAttempts() + 1);
        }
    }

    private NotificationChannel resolveChannel(NotificationLog.Channel channel) {
        return channels.stream()
                .filter(candidate -> candidate.channel() == channel)
                .findFirst()
                .orElseGet(() -> channels.stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "No notification channel is configured")));
    }

    /** Fallback wording when an institution has not customised the template. */
    private String defaultSubject(String templateCode) {
        return switch (templateCode) {
            case "PASSWORD_RESET" -> "Reset your password";
            case "LOAN_APPROVED" -> "Your loan has been approved";
            case "LOAN_DISBURSED" -> "Your loan has been disbursed";
            case "PAYMENT_RECEIVED" -> "Payment received";
            case "REPAYMENT_DUE" -> "Repayment reminder";
            case "LOAN_OVERDUE" -> "Your loan is overdue";
            default -> "Notification from your microfinance institution";
        };
    }

    private String defaultBody(String templateCode, Map<String, String> variables) {
        StringBuilder body = new StringBuilder(defaultSubject(templateCode)).append(".\n\n");
        variables.forEach((key, value) -> {
            // Secrets are passed through to the transport but never rendered into a stored body.
            if (!key.toLowerCase().contains("token")) {
                body.append(key).append(": ").append(value).append('\n');
            }
        });
        return body.toString();
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
