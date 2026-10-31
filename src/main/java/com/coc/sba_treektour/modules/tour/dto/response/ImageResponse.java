package com.coc.sba_treektour.modules.tour.dto.response;

public record ImageResponse(Long imageId, String imageUrl, String description, int sortOrder, Integer dayNumber, boolean cover) {}
