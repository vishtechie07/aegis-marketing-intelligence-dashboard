package com.aegis.controller;

import com.aegis.service.HarvestActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/harvest")
@RequiredArgsConstructor
public class HarvestStatusController {

    private final HarvestActivityService harvestActivityService;

    @GetMapping("/status")
    public Map<String, String> status() {
        return harvestActivityService.snapshotIso();
    }
}
