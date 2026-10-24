package com.coc.sba_treektour.modules.schedule.config;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ScheduleSettings {
    private final Duration holdDuration;
    private final Duration checkInBefore;
    private final Duration checkInAfter;
    private final Clock clock;

    @Autowired
    public ScheduleSettings(@Value("${schedule.hold-duration:PT15M}") Duration holdDuration,
            @Value("${schedule.check-in-before:PT2H}") Duration checkInBefore,
            @Value("${schedule.check-in-after:PT30M}") Duration checkInAfter) {
        this(holdDuration, checkInBefore, checkInAfter, Clock.systemUTC());
    }

    public ScheduleSettings(Duration holdDuration, Duration checkInBefore, Duration checkInAfter, Clock clock) {
        if (holdDuration.isNegative() || holdDuration.isZero() || checkInBefore.isNegative() || checkInAfter.isNegative())
            throw new IllegalArgumentException("Invalid schedule durations");
        this.holdDuration = holdDuration;
        this.checkInBefore = checkInBefore;
        this.checkInAfter = checkInAfter;
        this.clock = clock;
    }

    public OffsetDateTime now() { return OffsetDateTime.now(clock); }
    public Duration holdDuration() { return holdDuration; }
    public Duration checkInBefore() { return checkInBefore; }
    public Duration checkInAfter() { return checkInAfter; }
}
