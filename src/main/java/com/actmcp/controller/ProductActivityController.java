package com.actmcp.controller;

import com.actmcp.dto.PageResult;
import com.actmcp.dto.ProductActivityRequest;
import com.actmcp.model.ProductActivity;
import com.actmcp.service.ProductActivityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-activities")
public class ProductActivityController {

    private final ProductActivityService service;

    public ProductActivityController(ProductActivityService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<ProductActivity> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long actId,
            @RequestParam(required = false) String actType,
            @RequestParam(required = false) String keyword) {
        return service.list(page, size, actId, actType, keyword);
    }

    @GetMapping("/{id}")
    public ProductActivity get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ProductActivity> create(
            @Valid @RequestBody ProductActivityRequest request,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        ProductActivity created = service.create(request, operator);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ProductActivity update(
            @PathVariable long id,
            @Valid @RequestBody ProductActivityRequest request,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return service.update(id, request, operator);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
