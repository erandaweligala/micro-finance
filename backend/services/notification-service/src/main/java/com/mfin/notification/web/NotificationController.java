package com.mfin.notification.web;

import com.mfin.common.tenant.Roles;
import com.mfin.common.web.PageResponse;
import com.mfin.notification.application.NotificationService;
import com.mfin.notification.domain.NotificationLog;
import com.mfin.notification.web.NotificationDtos.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Outbound message history")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize(Roles.Has.MANAGEMENT)
    @Operation(summary = "List notifications sent for your institution",
            description = "Recipient addresses are masked; the full address is never returned.")
    public PageResponse<NotificationResponse> search(
            @RequestParam(required = false) NotificationLog.Status status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.search(status, pageable);
    }
}
