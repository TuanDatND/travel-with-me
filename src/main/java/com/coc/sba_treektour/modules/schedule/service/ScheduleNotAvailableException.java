package com.coc.sba_treektour.modules.schedule.service;

public class ScheduleNotAvailableException extends RuntimeException {
    public ScheduleNotAvailableException(String message) {
        super(message);
    }
}
