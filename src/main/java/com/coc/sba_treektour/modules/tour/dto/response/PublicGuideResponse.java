package com.coc.sba_treektour.modules.tour.dto.response;

import java.util.List;

public record PublicGuideResponse(
        Long guideId,
        String displayName,
        Integer experienceYears,
        String specialization,
        String bio,
        List<String> languages,
        String avatarUrl) {}
