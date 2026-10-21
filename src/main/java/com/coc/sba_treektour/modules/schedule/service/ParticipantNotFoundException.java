package com.coc.sba_treektour.modules.schedule.service;

public class ParticipantNotFoundException extends RuntimeException {
    public ParticipantNotFoundException(Long id) {
        super("Participant not found with id: " + id);
    }
}
