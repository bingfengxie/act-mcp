package com.actmcp.controller;

import com.actmcp.dto.PageResult;
import com.actmcp.dto.ProductActivityQuery;
import com.actmcp.dto.ProductActivityRequest;
import com.actmcp.model.ProductActivity;
import com.actmcp.service.ProductActivityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-activities")
public class ProductActivityController {

    private final ProductActivityService service;

    public ProductActivityController(ProductActivityService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<ProductActivity> list(@ModelAttribute ProductActivityQuery query) {
        return service.list(query);
    }

    @GetMapping("/{id}")
    public ProductActivity get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ProductActivity> create(@Valid @RequestBody ProductActivityRequest request) {
        ProductActivity created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping
    public ProductActivity update(@Valid @RequestBody ProductActivityRequest request) {
        return service.update(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
