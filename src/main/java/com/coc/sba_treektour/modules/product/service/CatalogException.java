package com.coc.sba_treektour.modules.product.service;

import org.springframework.http.HttpStatus;

public class CatalogException extends RuntimeException {
    private final HttpStatus status;
    public CatalogException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
    public static CatalogException missing(String message) { return new CatalogException(HttpStatus.NOT_FOUND, message); }
    public static CatalogException invalid(String message) { return new CatalogException(HttpStatus.BAD_REQUEST, message); }
    public static CatalogException conflict(String message) { return new CatalogException(HttpStatus.CONFLICT, message); }
}
