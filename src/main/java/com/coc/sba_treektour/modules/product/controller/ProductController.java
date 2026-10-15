package com.coc.sba_treektour.modules.product.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.product.dto.ProductDtos.*;
import com.coc.sba_treektour.modules.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController @RequestMapping("/api/products") @RequiredArgsConstructor
public class ProductController {
    private final ProductService products;
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category, @RequestParam(required = false) String mode,
            @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(products.list(keyword, category, mode, minPrice, maxPrice, null, false, page, size));
    }
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) { return ApiResponse.success(products.get(id, false)); }
}
