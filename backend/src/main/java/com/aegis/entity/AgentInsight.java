package com.aegis.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "agent_insights")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id", nullable = false)
    private CompetitorNews news;

    @Column(name = "agent_name", nullable = false, length = 50)
    private String agentName;

    @Column(length = 50)
    private String category;

    @Column(name = "threat_level")
    private Integer threatLevel;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "strategic_advice", columnDefinition = "TEXT")
    private String strategicAdvice;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @PrePersist
    void prePersist() {
        if (processedAt == null) processedAt = OffsetDateTime.now();
    }
}
