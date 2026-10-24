package com.coc.sba_treektour.modules.schedule.config;

import com.coc.sba_treektour.modules.schedule.repository.EventParticipantRepository;
import com.coc.sba_treektour.modules.schedule.service.HoldExpiryService;
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
public class ScheduleJobs {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduleJobs.class);
    private final EventParticipantRepository participants;
    private final HoldExpiryService expiry;
    private final ScheduleSettings settings;
    private final com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository schedules;
    public ScheduleJobs(EventParticipantRepository participants, HoldExpiryService expiry, ScheduleSettings settings,
            com.coc.sba_treektour.modules.schedule.repository.EventScheduleRepository schedules) {
        this.participants = participants; this.expiry = expiry; this.settings = settings;
        this.schedules = schedules;
    }
    @Scheduled(fixedDelayString = "${schedule.expiry.interval:PT1M}", initialDelayString = "${schedule.expiry.interval:PT1M}")
    public void expireHolds() {
        var ids = new java.util.TreeSet<>(participants.findExpiredScheduleIds(settings.now(), PageRequest.of(0, 100)));
        ids.addAll(schedules.findDueToClose(settings.now(), PageRequest.of(0, 100)));
        for (Long id : ids) {
            try { expiry.expireSchedule(id); }
            catch (RuntimeException ex) { LOG.error("Cannot expire holds for schedule {}", id, ex); }
        }
    }
}
