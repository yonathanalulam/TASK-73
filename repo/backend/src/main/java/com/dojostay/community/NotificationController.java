package com.dojostay.community;

import com.dojostay.common.ApiResponse;
import com.dojostay.community.dto.NotificationResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('notifications.read')")
    public ApiResponse<List<NotificationResponse>> listMine() {
        return ApiResponse.ok(notificationService.listMine());
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notifications.read')")
    public ApiResponse<Map<String, Long>> unread() {
        return ApiResponse.ok(Map.of("unread", notificationService.unreadCount()));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notifications.read')")
    public ApiResponse<NotificationResponse> markRead(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(id));
    }
}
