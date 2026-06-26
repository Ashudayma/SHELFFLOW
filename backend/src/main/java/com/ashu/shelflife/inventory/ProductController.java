package com.ashu.shelflife.inventory;

import com.ashu.shelflife.inventory.dto.CreateProductRequest;
import com.ashu.shelflife.inventory.dto.ProductResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SKU / product master-data management. Administration only (CENTRAL_ADMIN, global scope).
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    public List<ProductResponse> list() {
        return productService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('CENTRAL_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }
}
