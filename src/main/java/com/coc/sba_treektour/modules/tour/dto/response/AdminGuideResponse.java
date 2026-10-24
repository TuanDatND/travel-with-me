package com.coc.sba_treektour.modules.tour.dto.response;

import com.coc.sba_treektour.modules.tour.enums.GuideStatus;

import java.util.List;

public record AdminGuideResponse(
        Long guideId,
        Long userId,
        String displayName,
        Integer experienceYears,
        String specialization,
        GuideStatus status,
        String bio,
        List<String> languages,
        String avatarUrl) {}
