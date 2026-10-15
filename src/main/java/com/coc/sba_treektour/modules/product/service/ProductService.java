package com.coc.sba_treektour.modules.product.service;

import com.coc.sba_treektour.modules.product.dto.ProductDtos.*;
import com.coc.sba_treektour.modules.product.entity.Product;
import com.coc.sba_treektour.modules.product.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository products;

    public PageResponse<ProductResponse> list(String keyword, String category, String mode,
            BigDecimal minPrice, BigDecimal maxPrice, Status status, boolean admin, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw CatalogException.invalid("page >= 0; size từ 1 đến 100");
        if (mode != null && !mode.equals("SALE") && !mode.equals("RENTAL"))
            throw CatalogException.invalid("mode phải là SALE hoặc RENTAL");
        if ((minPrice != null || maxPrice != null) && mode == null)
            throw CatalogException.invalid("Cần chọn mode khi lọc giá");
        if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0
                || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw CatalogException.invalid("Khoảng giá không hợp lệ");
        var result = products.findAll((root, query, cb) -> {
            var filters = new ArrayList<Predicate>();
            if (!admin || status != null) filters.add(cb.equal(root.get("status"), admin ? status.name() : "ACTIVE"));
            if (keyword != null && !keyword.isBlank()) {
                String literal = keyword.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                filters.add(cb.like(cb.lower(root.get("name")), "%" + literal + "%", '\\'));
            }
            if (category != null && !category.isBlank()) filters.add(cb.equal(cb.lower(root.get("category")), category.strip().toLowerCase(Locale.ROOT)));
            if (mode != null) {
                var price = root.<BigDecimal>get(mode.equals("SALE") ? "salePrice" : "rentalPricePerDay");
                filters.add(cb.isNotNull(price));
                if (minPrice != null) filters.add(cb.greaterThanOrEqualTo(price, minPrice));
                if (maxPrice != null) filters.add(cb.lessThanOrEqualTo(price, maxPrice));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new PageResponse<>(result.getContent().stream().map(this::response).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    public ProductResponse get(Long id, boolean admin) {
        Product product = find(id);
        if (!admin && !product.getStatus().equals("ACTIVE")) throw CatalogException.missing("Không tìm thấy thiết bị");
        return response(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        return response(products.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = products.lockById(id).orElseThrow(() -> CatalogException.missing("Không tìm thấy thiết bị"));
        apply(product, request);
        return response(product);
    }

    @Transactional
    public ProductResponse status(Long id, Status status) {
        Product product = products.lockById(id).orElseThrow(() -> CatalogException.missing("Không tìm thấy thiết bị"));
        product.setStatus(status.name());
        return response(product);
    }

    public Product find(Long id) {
        return products.findById(id).orElseThrow(() -> CatalogException.missing("Không tìm thấy thiết bị"));
    }

    private void apply(Product product, ProductRequest request) {
        if (request.salePrice() == null && request.rentalPricePerDay() == null)
            throw CatalogException.invalid("Thiết bị cần có giá bán hoặc giá thuê");
        product.setName(request.name().strip());
        product.setCategory(request.category() == null ? null : request.category().strip());
        product.setSalePrice(request.salePrice());
        product.setRentalPricePerDay(request.rentalPricePerDay());
    }

    private ProductResponse response(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getCategory(), product.getSalePrice(),
                product.getRentalPricePerDay(), product.getStatus(), product.getCreatedAt());
    }
}
