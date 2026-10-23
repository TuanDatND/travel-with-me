package com.coc.sba_treektour.modules.schedule.service.impl;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleHistoryRepository;
import com.coc.sba_treektour.modules.schedule.entity.EventScheduleHistory;
import com.coc.sba_treektour.modules.schedule.entity.ScheduleStatus;
import com.coc.sba_treektour.modules.schedule.service.HoldExpiryService;
import com.coc.sba_treektour.modules.schedule.service.ParticipantLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldExpiryServiceImpl implements HoldExpiryService {
    private final EventScheduleRepository schedules;
    private final ParticipantLifecycle lifecycle;
    private final ScheduleSettings settings;
    private final EventScheduleHistoryRepository histories;
    public HoldExpiryServiceImpl(EventScheduleRepository schedules, ParticipantLifecycle lifecycle, ScheduleSettings settings,
            EventScheduleHistoryRepository histories) {
        this.schedules = schedules; this.lifecycle = lifecycle; this.settings = settings;
        this.histories = histories;
    }
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireSchedule(Long id) {
        schedules.findForUpdate(id).ifPresent(s -> {
            lifecycle.expire(s, settings.now());
            if (!settings.now().isBefore(s.getRegistrationClosesAt())
                    && (s.getStatus() == ScheduleStatus.OPEN || s.getStatus() == ScheduleStatus.FULL)) {
                var old = s.getStatus();
                s.updateStatus(ScheduleStatus.CLOSED);
                histories.save(new EventScheduleHistory(s, null, null, null, null, old.name(), "CLOSED", "Tự động đóng đăng ký khi đến hạn"));
            }
        });
    }
}
