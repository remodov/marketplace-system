package ru.remodov.backoffice.moderation.exception;

public abstract class BackofficeException extends RuntimeException {

    protected BackofficeException(String message) {
        super(message);
    }

    protected BackofficeException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract String code();

    public abstract int status();
}
