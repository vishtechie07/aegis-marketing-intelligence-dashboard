package com.aegis.dto;

/** REST request body for the "Ask Agent" deep-dive feature. */
public record DeepDiveRequest(Long newsId, String question) {}
