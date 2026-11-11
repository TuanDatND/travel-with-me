package com.coc.sba_treektour.modules.product.service;

import com.coc.sba_treektour.modules.product.dto.request.ProductRequest;
import com.coc.sba_treektour.modules.product.dto.response.ProductPageResponse;
import com.coc.sba_treektour.modules.product.dto.response.ProductResponse;
import com.coc.sba_treektour.modules.product.enums.ProductStatus;
import java.math.BigDecimal;

public interface ProductService {
    ProductPageResponse<ProductResponse> list(String keyword, String category, String mode,
            BigDecimal minPrice, BigDecimal maxPrice, ProductStatus status, boolean admin, int page, int size);
    ProductResponse get(Long id, boolean admin);
    ProductResponse create(ProductRequest request);
    ProductResponse update(Long id, ProductRequest request);
    ProductResponse status(Long id, ProductStatus status);
}
