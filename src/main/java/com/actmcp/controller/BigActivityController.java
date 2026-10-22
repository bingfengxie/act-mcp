package com.actmcp.controller;

import com.actmcp.dto.ActivityOption;
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
