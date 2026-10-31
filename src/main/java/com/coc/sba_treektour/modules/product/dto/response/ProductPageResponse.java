package com.coc.sba_treektour.modules.product.dto.response;

import java.util.List;

public record ProductPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
