package ru.vikulinva.notificationservice.error;

import java.util.UUID;

public final class NotificationNotFoundException extends NotificationException {

    public NotificationNotFoundException(UUID id) {
        super("NOTIFICATION_NOT_FOUND", 404, "Notification " + id + " not found");
    }
}
