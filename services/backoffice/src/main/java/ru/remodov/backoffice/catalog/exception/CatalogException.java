package ru.remodov.backoffice.catalog.exception;

public abstract class CatalogException extends RuntimeException {
    protected CatalogException(String message) {
        super(message);
    }

    protected CatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
