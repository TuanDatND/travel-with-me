package com.coc.sba_treektour.modules.schedule.service.impl;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.entity.EventScheduleHistory;
import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleHistoryRepository;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.modules.schedule.service.HoldExpiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HoldExpiryServiceImpl implements HoldExpiryService {
    private final EventScheduleRepository eventScheduleRepository;
    private final ParticipantLifecycle participantLifecycle;
    private final ScheduleSettings scheduleSettings;
    private final EventScheduleHistoryRepository eventScheduleHistoryRepository;
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireSchedule(Long id) {
        eventScheduleRepository.findForUpdate(id).ifPresent(s -> {
            participantLifecycle.expire(s, scheduleSettings.now());
            if (!scheduleSettings.now().isBefore(s.getRegistrationClosesAt())
                    && (s.getStatus() == ScheduleStatus.OPEN || s.getStatus() == ScheduleStatus.FULL)) {
                var old = s.getStatus();
                s.updateStatus(ScheduleStatus.CLOSED);
                eventScheduleHistoryRepository.save(new EventScheduleHistory(s, null, null, null, null, old.name(), "CLOSED", "Tự động đóng đăng ký khi đến hạn"));
            }
        });
    }
}
