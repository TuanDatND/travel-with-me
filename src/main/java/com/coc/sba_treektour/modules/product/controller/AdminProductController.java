package com.coc.sba_treektour.modules.product.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.product.dto.request.ProductRequest;
import com.coc.sba_treektour.modules.product.dto.request.StatusRequest;
import com.coc.sba_treektour.modules.product.dto.response.ProductPageResponse;
import com.coc.sba_treektour.modules.product.dto.response.ProductResponse;
import com.coc.sba_treektour.modules.product.enums.ProductStatus;
import com.coc.sba_treektour.modules.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;

@RestController @RequestMapping("/api/admin/products") @RequiredArgsConstructor
public class AdminProductController {
    private final ProductService products;
    @GetMapping
    public ApiResponse<ProductPageResponse<ProductResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category, @RequestParam(required = false) String mode,
            @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(products.list(keyword, category, mode, minPrice, maxPrice, status, true, page, size));
    }
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) { return ApiResponse.success(products.get(id, true)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.created("Đã tạo thiết bị", products.create(request));
    }
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ApiResponse.success(products.update(id, request));
    }
    @PatchMapping("/{id}/status")
    public ApiResponse<ProductResponse> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(products.status(id, request.status()));
    }
}
