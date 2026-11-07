package com.coc.sba_treektour.modules.product.service.impl;

import com.coc.sba_treektour.common.exception.CatalogException;
import com.coc.sba_treektour.modules.product.repository.ProductRepository;
import com.coc.sba_treektour.modules.product.service.ProductImageService;
import com.coc.sba_treektour.modules.product.storage.ProductImageStorage;
import com.coc.sba_treektour.modules.product.validator.ProductImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImageServiceImpl implements ProductImageService {
    private final ProductRepository products;
    private final ProductImageStorage storage;
    private final ProductImageValidator validator;

    @Override
    @Transactional
    public void upload(Long id, MultipartFile file) {
        byte[] content = validator.validate(file);
        var product = products.lockById(id)
                .orElseThrow(() -> CatalogException.missing("Equipment not found"));
        var uploaded = storage.upload(content);
        String previous = product.getImagePublicId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                String obsolete = status == STATUS_COMMITTED ? previous : uploaded.publicId();
                if (obsolete != null) {
                    try {
                        storage.delete(obsolete);
                    } catch (RuntimeException error) {
                        log.error("Product image cleanup required for publicId={}", obsolete, error);
                    }
                }
            }
        });
        product.setImageUrl(uploaded.secureUrl());
        product.setImagePublicId(uploaded.publicId());
    }
}
