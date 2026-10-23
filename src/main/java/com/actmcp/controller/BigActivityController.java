package com.actmcp.controller;

import com.actmcp.dto.ActivityOption;
import com.actmcp.dto.BigActivityBundleRequest;
import com.actmcp.dto.BigActivityBundleResponse;
import com.actmcp.dto.BigActivityQuery;
import com.actmcp.dto.BigActivityRequest;
import com.actmcp.dto.PageResult;
import com.actmcp.model.BigActivity;
import com.actmcp.service.BigActivityService;
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

import java.util.List;

@RestController
@RequestMapping("/api/big-activities")
public class BigActivityController {

    private final BigActivityService service;

    public BigActivityController(BigActivityService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<BigActivity> list(@ModelAttribute BigActivityQuery query) {
        return service.list(query);
    }

    @GetMapping("/options")
    public List<ActivityOption> options() {
        return service.options();
    }

    @GetMapping("/{id}")
    public BigActivity get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<BigActivity> create(@Valid @RequestBody BigActivityRequest request) {
        BigActivity created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 一次创建 1 个大活动和多个商品活动，商品活动自动挂到新建的大活动下。
     */
    @PostMapping("/with-products")
    public ResponseEntity<BigActivityBundleResponse> createWithProducts(
            @Valid @RequestBody BigActivityBundleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createWithProducts(request));
    }

    @PutMapping
    public BigActivity update(@Valid @RequestBody BigActivityRequest request) {
        return service.update(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
