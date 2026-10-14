package com.coc.sba_treektour.modules.order.service;

public class InvalidOrderException extends RuntimeException {
    public InvalidOrderException(String message) { super(message); }
}
