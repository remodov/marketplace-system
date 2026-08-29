package ru.vikulinva.notificationservice.error;

import ru.vikulinva.notificationservice.generated.enums.NotificationStatus;

public final class InvalidStatusForRetryException extends NotificationException {

    public InvalidStatusForRetryException(NotificationStatus current) {
        super("INVALID_STATUS_FOR_RETRY", 409,
            "Cannot retry: current status is " + current + " (only FAILED is retriable)");
    }
}
