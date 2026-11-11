package com.orderplatform.order.service;

import com.orderplatform.order.domain.Product;
import com.orderplatform.order.dto.request.CreateProductRequest;
import com.orderplatform.order.dto.response.ProductResponse;
import com.orderplatform.order.exception.ResourceNotFoundException;
import com.orderplatform.order.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResponse create(CreateProductRequest req) {
        Instant now = Instant.now();
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setStockQuantity(req.stockQuantity());
        p.setReservedQuantity(0);
        p.setActive(true);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return toResponse(productRepository.save(p));
    }

    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findAllByActiveTrue(pageable).map(this::toResponse);
    }

    public ProductResponse findById(UUID id) {
        return productRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(), p.getName(), p.getDescription(),
                p.getPrice(), p.getStockQuantity(), p.getActive(), p.getCreatedAt()
        );
    }
}
