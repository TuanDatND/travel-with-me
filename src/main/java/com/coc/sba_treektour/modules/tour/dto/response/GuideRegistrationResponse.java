package com.coc.sba_treektour.modules.tour.dto.response;


public record GuideRegistrationResponse(
        AdminGuideResponse guide, String email, String temporaryPassword, boolean emailSent) {}
