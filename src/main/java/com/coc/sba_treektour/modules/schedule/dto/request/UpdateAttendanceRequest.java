package com.coc.sba_treektour.modules.schedule.dto.request;

import com.coc.sba_treektour.modules.schedule.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAttendanceRequest(@NotNull AttendanceStatus status) {}
