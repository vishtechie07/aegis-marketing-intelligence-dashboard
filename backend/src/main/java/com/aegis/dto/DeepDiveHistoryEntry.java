package com.aegis.dto;

import java.time.OffsetDateTime;

public record DeepDiveHistoryEntry(Long id, Long newsId, String question, String analysis, OffsetDateTime createdAt) {}
