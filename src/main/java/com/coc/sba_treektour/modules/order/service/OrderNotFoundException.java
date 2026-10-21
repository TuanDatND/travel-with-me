package com.coc.sba_treektour.modules.order.service;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long id) { super("Order not found: " + id); }
}
