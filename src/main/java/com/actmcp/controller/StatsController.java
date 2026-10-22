package com.actmcp.controller;

import com.actmcp.dto.Stats;
import com.actmcp.service.BigActivityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final BigActivityService bigActivityService;

    public StatsController(BigActivityService bigActivityService) {
        this.bigActivityService = bigActivityService;
    }

    @GetMapping
    public Stats stats() {
        return bigActivityService.stats();
    }
}
