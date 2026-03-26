package com.aegis.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class HarvestActivityService {

    private final ConcurrentHashMap<String, Instant> lastRunUtc = new ConcurrentHashMap<>();

    public void record(String sourceKey) {
        if (sourceKey != null && !sourceKey.isBlank()) {
            lastRunUtc.put(sourceKey.trim(), Instant.now());
        }
    }

    public Map<String, String> snapshotIso() {
        return lastRunUtc.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
    }
}
