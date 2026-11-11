package com.coc.sba_treektour.modules.tour.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ImageLayoutInput(@NotNull List<@NotNull @Valid ImagePlacementInput> images) {}
