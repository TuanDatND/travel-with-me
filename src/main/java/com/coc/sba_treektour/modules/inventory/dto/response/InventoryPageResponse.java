package com.coc.sba_treektour.modules.inventory.dto.response;

import java.util.List;

public record InventoryPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
