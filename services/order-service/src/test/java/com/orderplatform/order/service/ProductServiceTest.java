package com.orderplatform.order.service;

import com.orderplatform.order.domain.Product;
import com.orderplatform.order.dto.request.CreateProductRequest;
import com.orderplatform.order.dto.response.ProductResponse;
import com.orderplatform.order.exception.ResourceNotFoundException;
import com.orderplatform.order.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void create_setsDefaultsAndSaves() {
        CreateProductRequest req = new CreateProductRequest("Widget", "A test widget", new BigDecimal("9.99"), 50);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        when(productRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        productService.create(req);

        Product saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getReservedQuantity()).isEqualTo(0);
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void create_returnsProductResponse() {
        CreateProductRequest req = new CreateProductRequest("Gadget", "desc", new BigDecimal("5.00"), 10);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.create(req);

        assertThat(response.name()).isEqualTo("Gadget");
        assertThat(response.price()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(response.stockQuantity()).isEqualTo(10);
        assertThat(response.active()).isTrue();
    }

    @Test
    void findAll_delegatesToRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.findAllByActiveTrue(pageable)).thenReturn(page);

        productService.findAll(pageable);

        verify(productRepository).findAllByActiveTrue(pageable);
    }

    @Test
    void findById_returnsResponse_whenActiveProduct() {
        UUID id = UUID.randomUUID();
        Product p = new Product();
        p.setId(id);
        p.setName("Active");
        p.setPrice(new BigDecimal("1.00"));
        p.setStockQuantity(5);
        p.setActive(true);
        p.setCreatedAt(Instant.now());

        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        ProductResponse response = productService.findById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Active");
    }

    @Test
    void findById_throwsResourceNotFoundException_whenInactive() {
        UUID id = UUID.randomUUID();
        Product p = new Product();
        p.setId(id);
        p.setActive(false);

        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> productService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_throwsResourceNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
