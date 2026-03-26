package com.aegis.controller;

import com.aegis.dto.CompetitorDto;
import com.aegis.service.CompetitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/competitors")
@RequiredArgsConstructor
public class CompetitorController {

    private final CompetitorService service;

    @GetMapping
    public List<CompetitorDto> list() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<CompetitorDto> add(@RequestBody CompetitorDto dto) {
        if (dto.name() == null || dto.name().isBlank())
            return ResponseEntity.badRequest().build();
        service.add(dto);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> remove(@PathVariable String name) {
        return service.remove(name)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** AI-powered lookup — runs on a non-blocking thread */
    @GetMapping("/lookup")
    public Mono<CompetitorDto> lookup(
            @RequestParam String name,
            @RequestParam(required = false) String country) {
        return Mono.fromCallable(() -> service.lookup(name, country))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/count")
    public Map<String, Integer> count() {
        return Map.of("count", service.getNames().size());
    }
}
