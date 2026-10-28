package com.coc.sba_treektour.modules.schedule.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

/** Omitted guideId keeps the assignment; explicit null removes it. */
public class PatchScheduleRequest {
    @Getter
    @Positive
    private Long guideId;

    @Getter
    @Min(1)
    private Integer maxParticipants;

    private boolean guideIdPresent;
    private boolean maxParticipantsPresent;

    @JsonSetter("guideId")
    public void setGuideId(Long guideId) {
        this.guideId = guideId;
        this.guideIdPresent = true;
    }

    @JsonSetter("maxParticipants")
    public void setMaxParticipants(Integer maxParticipants) {
        this.maxParticipants = maxParticipants;
        this.maxParticipantsPresent = true;
    }

    @JsonIgnore
    public boolean isGuideIdPresent() {
        return guideIdPresent;
    }

    @JsonIgnore
    @AssertTrue(message = "Provide guideId or a non-null maxParticipants")
    public boolean isUpdateValid() {
        return (guideIdPresent || maxParticipantsPresent)
                && (!maxParticipantsPresent || maxParticipants != null);
    }
}
