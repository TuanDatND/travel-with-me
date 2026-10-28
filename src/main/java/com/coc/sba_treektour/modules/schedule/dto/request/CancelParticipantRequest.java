package com.coc.sba_treektour.modules.schedule.dto.request;

import jakarta.validation.constraints.Size;

public record CancelParticipantRequest(@Size(max = 2000) String reason) {}
