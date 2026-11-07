package com.coc.sba_treektour.modules.product;

import com.coc.sba_treektour.common.exception.CatalogException;
import com.coc.sba_treektour.modules.product.entity.Product;
import com.coc.sba_treektour.modules.product.repository.ProductRepository;
import com.coc.sba_treektour.modules.product.service.impl.ProductImageServiceImpl;
import com.coc.sba_treektour.modules.product.storage.ProductImageStorage;
import com.coc.sba_treektour.modules.product.validator.ProductImageValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Base64;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductImageTests {
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageStorage storage = mock(ProductImageStorage.class);
    private final ProductImageValidator validator = new ProductImageValidator();
    private final ProductImageServiceImpl service = new ProductImageServiceImpl(products, storage, validator);
    private final Product product = new Product();
    private MockMultipartFile png() {
        return new MockMultipartFile("file", "test.png", "image/png", Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1cAAAAASUVORK5CYII="));
    }
    @BeforeEach void setup() {
        product.setImagePublicId("products/old");
        when(products.lockById(1L)).thenReturn(Optional.of(product));
        when(storage.upload(any())).thenReturn(new ProductImageStorage.StoredImage("https://example.test/new.png", "products/new"));
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }
    @Test void commitDeletesOldImageOnlyAfterCommit() {
        service.upload(1L, png());
        assertThat(product.getImageUrl()).isEqualTo("https://example.test/new.png");
        verify(storage, never()).delete(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(storage).delete("products/old");
        verify(storage, never()).delete("products/new");
    }
    @Test void rollbackDeletesNewImageAndPreservesOldAsset() {
        service.upload(1L, png());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).delete("products/new");
        verify(storage, never()).delete("products/old");
    }
    @Test void missingProductDoesNotUpload() {
        assertThatThrownBy(() -> service.upload(2L, png())).isInstanceOf(CatalogException.class);
        verify(storage, never()).upload(any());
    }
    @Test void providerFailurePreservesMetadata() {
        when(storage.upload(any())).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> service.upload(1L, png())).isInstanceOf(IllegalStateException.class);
        assertThat(product.getImagePublicId()).isEqualTo("products/old");
    }
    @Test void rejectsSpoofedMimeAndEmptyFiles() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", "fake.png", "image/png", new byte[]{1,2,3}))).isInstanceOf(CatalogException.class);
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", new byte[0]))).isInstanceOf(CatalogException.class);
    }
    @Test void rejectsOversizedAndUnsupportedFiles() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]))).isInstanceOf(CatalogException.class);
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile("file", "x.svg", "image/svg+xml", new byte[]{1}))).isInstanceOf(CatalogException.class);
    }
}
