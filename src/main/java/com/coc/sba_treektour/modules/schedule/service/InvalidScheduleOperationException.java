package com.coc.sba_treektour.modules.schedule.service;

public class InvalidScheduleOperationException extends RuntimeException {
    public InvalidScheduleOperationException(String message) {
        super(message);
    }
}
