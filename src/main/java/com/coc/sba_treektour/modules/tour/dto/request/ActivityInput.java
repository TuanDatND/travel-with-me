package com.coc.sba_treektour.modules.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActivityInput(
        @NotBlank @Size(max = 100) String time,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String location,
        @Size(max = 2000) String description,
        @Pattern(regexp = "meet|hike|food|summit|camp|rest|flag") String icon) {}
