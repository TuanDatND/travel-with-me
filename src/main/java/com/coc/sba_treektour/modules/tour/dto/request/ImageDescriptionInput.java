package com.coc.sba_treektour.modules.tour.dto.request;

import jakarta.validation.constraints.Size;

public record ImageDescriptionInput(@Size(max = 1000) String description) {}
