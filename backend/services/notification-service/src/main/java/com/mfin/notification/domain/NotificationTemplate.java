package com.mfin.notification.domain;

import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.util.Map;

/**
 * A message template owned by an institution, so wording and tone stay theirs rather than the
 * platform's. Placeholders are {@code {{name}}} style and are substituted, never evaluated -
 * a template is data, not code.
 */
@Entity
@Table(name = "notification_template",
        uniqueConstraints = @UniqueConstraint(name = "ux_template_tenant_code_channel",
                columnNames = {"tenant_id", "code", "channel"}))
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class NotificationTemplate extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationLog.Channel channel;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected NotificationTemplate() {
    }

    public NotificationTemplate(String code, NotificationLog.Channel channel, String subject,
                                String body) {
        this.code = code;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
    }

    /** Substitutes {{placeholders}}; unknown placeholders are left visible rather than silently blanked. */
    public String renderBody(Map<String, String> variables) {
        return substitute(body, variables);
    }

    public String renderSubject(Map<String, String> variables) {
        return subject == null ? null : substitute(subject, variables);
    }

    private String substitute(String template, Map<String, String> variables) {
        String rendered = template;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            rendered = rendered.replace("{{" + variable.getKey() + "}}",
                    variable.getValue() == null ? "" : variable.getValue());
        }
        return rendered;
    }

    public String getCode() {
        return code;
    }

    public NotificationLog.Channel getChannel() {
        return channel;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public boolean isActive() {
        return active;
    }

    public void update(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }
}
