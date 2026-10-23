package com.coc.sba_treektour.modules.schedule.service;

/** Internal worker contract, not exposed through controllers. */
public interface HoldExpiryService {
    void expireSchedule(Long scheduleId);
}
