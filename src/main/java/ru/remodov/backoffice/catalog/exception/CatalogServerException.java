package ru.remodov.backoffice.catalog.exception;

import lombok.Getter;

@Getter
public class CatalogServerException extends CatalogException {
    private final int status;

    public CatalogServerException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
