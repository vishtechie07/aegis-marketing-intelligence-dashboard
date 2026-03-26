package com.aegis.dto;

public record CompetitorDto(
        String name,
        String githubOrg,
        String description,
        String ticker,
        String industry,
        String country
) {
    /** Optional ticker/industry/country. */
    public CompetitorDto(String name, String githubOrg, String description) {
        this(name, githubOrg, description, null, null, null);
    }
}
