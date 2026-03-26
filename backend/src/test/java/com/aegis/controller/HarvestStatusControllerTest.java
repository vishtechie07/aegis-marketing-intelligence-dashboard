package com.aegis.controller;

import com.aegis.service.HarvestActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.mockito.Mockito.when;

@WebFluxTest(HarvestStatusController.class)
class HarvestStatusControllerTest {

    @Autowired WebTestClient client;
    @MockitoBean HarvestActivityService harvestActivityService;

    @Test
    void status_returnsMap() {
        when(harvestActivityService.snapshotIso()).thenReturn(Map.of("RSS", "2026-01-01T00:00:00Z"));
        client.get().uri("/api/harvest/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.RSS").isEqualTo("2026-01-01T00:00:00Z");
    }
}
