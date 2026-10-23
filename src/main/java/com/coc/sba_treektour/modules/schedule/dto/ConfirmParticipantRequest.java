package com.coc.sba_treektour.modules.schedule.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
public record ConfirmParticipantRequest(@NotNull UUID registrationReference,
        @NotBlank @Size(max = 180) String paymentReference) {}
