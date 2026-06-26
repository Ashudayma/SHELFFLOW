package com.ashu.shelflife.inventory;

import com.ashu.shelflife.common.error.ConflictException;
import com.ashu.shelflife.inventory.dto.CreateProductRequest;
import com.ashu.shelflife.inventory.dto.ProductResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listAll() {
        return productRepository.findAll().stream().map(ProductResponse::from).toList();
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsById(request.sku())) {
            throw new ConflictException("Product already exists for sku: " + request.sku());
        }
        Product product = new Product();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setBarcode(request.barcode());
        product.setUnit(request.unit());
        return ProductResponse.from(productRepository.save(product));
    }
}
