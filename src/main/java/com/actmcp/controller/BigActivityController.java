package com.actmcp.controller;

import com.actmcp.dto.ActivityOption;
import com.actmcp.dto.BigActivityRequest;
import com.actmcp.dto.PageResult;
import com.actmcp.model.BigActivity;
import com.actmcp.service.BigActivityService;
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

import java.util.List;

@RestController
@RequestMapping("/api/big-activities")
public class BigActivityController {

    private final BigActivityService service;

    public BigActivityController(BigActivityService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<BigActivity> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String actType,
            @RequestParam(required = false) String audienceScope) {
        return service.list(page, size, keyword, actType, audienceScope);
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
    public ResponseEntity<BigActivity> create(
            @Valid @RequestBody BigActivityRequest request,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        BigActivity created = service.create(request, operator);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public BigActivity update(
            @PathVariable long id,
            @Valid @RequestBody BigActivityRequest request,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return service.update(id, request, operator);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
