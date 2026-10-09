package com.coc.sba_treektour.schedule.service;

public class InvalidScheduleOperationException extends RuntimeException {
    public InvalidScheduleOperationException(String message) {
        super(message);
    }
}
