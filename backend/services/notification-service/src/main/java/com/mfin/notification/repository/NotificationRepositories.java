package com.mfin.notification.repository;

import com.mfin.notification.domain.NotificationLog;
import com.mfin.notification.domain.NotificationTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class NotificationRepositories {

    private NotificationRepositories() {
    }

    public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

        boolean existsByTenantIdAndSourceEventId(UUID tenantId, UUID sourceEventId);

        @Query("""
                select n from NotificationLog n
                where n.tenantId = :tenantId
                  and (:status is null or n.status = :status)
                order by n.createdAt desc
                """)
        Page<NotificationLog> search(@Param("tenantId") UUID tenantId,
                                     @Param("status") NotificationLog.Status status,
                                     Pageable pageable);

        /** Messages awaiting a retry, for the redelivery job. */
        List<NotificationLog> findTop100ByStatusOrderByCreatedAtAsc(NotificationLog.Status status);
    }

    public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

        Optional<NotificationTemplate> findByTenantIdAndCodeAndChannel(
                UUID tenantId, String code, NotificationLog.Channel channel);

        List<NotificationTemplate> findByTenantId(UUID tenantId);
    }
}
