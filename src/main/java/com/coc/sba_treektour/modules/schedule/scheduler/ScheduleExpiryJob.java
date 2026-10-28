package com.coc.sba_treektour.modules.schedule.scheduler;

import com.coc.sba_treektour.modules.schedule.config.ScheduleSettings;
import com.coc.sba_treektour.modules.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository;
import com.coc.sba_treektour.modules.schedule.service.HoldExpiryService;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "schedule.expiry.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ScheduleExpiryJob {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduleExpiryJob.class);
    private final EventParticipantRepository eventParticipantRepository;
    private final HoldExpiryService holdExpiryService;
    private final ScheduleSettings scheduleSettings;
    private final EventScheduleRepository eventScheduleRepository;
    @Scheduled(fixedDelayString = "${schedule.expiry.interval:PT1M}", initialDelayString = "${schedule.expiry.interval:PT1M}")
    public void expireHolds() {
        var ids = new TreeSet<>(eventParticipantRepository.findExpiredScheduleIds(scheduleSettings.now(), PageRequest.of(0, 100)));
        ids.addAll(eventScheduleRepository.findDueToClose(scheduleSettings.now(), PageRequest.of(0, 100)));
        for (Long id : ids) {
            try { holdExpiryService.expireSchedule(id); }
            catch (RuntimeException ex) { LOG.error("Cannot expire holds for schedule {}", id, ex); }
        }
    }
}
