package com.coc.sba_treektour.modules.event.dto.response;

import com.coc.sba_treektour.modules.event.enums.DifficultyLevel;

public record DetailsResponse(
        Integer durationMinutes,
        DifficultyLevel difficultyLevel,
        String meetingPoint,
        String requirements) {}
