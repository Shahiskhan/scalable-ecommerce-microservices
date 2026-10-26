package com.acme.commerce.notification.api;
import com.acme.commerce.notification.application.NotificationService;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    @GetMapping public List<NotificationService.Notice> list(@RequestAttribute String userId) { return notifications.list(userId); }
}
