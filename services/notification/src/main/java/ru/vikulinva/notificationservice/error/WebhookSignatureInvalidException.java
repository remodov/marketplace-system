package ru.vikulinva.notificationservice.error;

public final class WebhookSignatureInvalidException extends NotificationException {

    public WebhookSignatureInvalidException(String detail) {
        super("WEBHOOK_SIGNATURE_INVALID", 401, detail);
    }
}
