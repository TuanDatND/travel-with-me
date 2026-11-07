package com.coc.sba_treektour.modules.product.service;

import org.springframework.web.multipart.MultipartFile;

public interface ProductImageService {
    void upload(Long id, MultipartFile file);
}
