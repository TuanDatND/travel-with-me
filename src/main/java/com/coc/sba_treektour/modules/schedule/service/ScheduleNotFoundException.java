package com.coc.sba_treektour.modules.schedule.service;

public class ScheduleNotFoundException extends RuntimeException {
    public ScheduleNotFoundException(Long id) {
        super("Event schedule not found with id: " + id);
    }
}
