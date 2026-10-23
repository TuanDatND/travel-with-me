package com.coc.sba_treektour.modules.schedule.dto;
import jakarta.validation.constraints.Size;
public record CancelParticipantRequest(@Size(max = 2000) String reason) {}
