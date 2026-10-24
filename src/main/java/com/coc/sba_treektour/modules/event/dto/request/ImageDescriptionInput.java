package com.coc.sba_treektour.modules.event.dto.request;

import jakarta.validation.constraints.Size;

public record ImageDescriptionInput(@Size(max = 1000) String description) {}
