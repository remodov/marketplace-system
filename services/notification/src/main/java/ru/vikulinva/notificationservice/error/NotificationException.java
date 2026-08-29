package ru.vikulinva.notificationservice.error;

/** Базовое доменное исключение Notification: несёт {@code code} и HTTP-статус. */
public abstract class NotificationException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    protected NotificationException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
}
