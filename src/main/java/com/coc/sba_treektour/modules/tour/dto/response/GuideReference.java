package com.coc.sba_treektour.modules.tour.dto.response;

import com.coc.sba_treektour.modules.tour.enums.GuideStatus;

public record GuideReference(
        Long guideId, Long userId, GuideStatus status, boolean accountActive) {}
