package com.aegis.repository;

import com.aegis.entity.AgentInsight;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentInsightRepository extends JpaRepository<AgentInsight, Long> {

    List<AgentInsight> findByNewsIdOrderByProcessedAtDesc(Long newsId);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            ORDER BY i.processedAt DESC
            """)
    List<AgentInsight> findLatestWithNews(Pageable pageable);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            WHERE n.competitorName = :name
            ORDER BY i.processedAt DESC
            """)
    List<AgentInsight> findLatestWithNewsByCompetitor(@Param("name") String competitorName, Pageable pageable);

    @Query("""
            SELECT i FROM AgentInsight i
            JOIN FETCH i.news n
            WHERE i.threatLevel >= :minLevel
            ORDER BY i.threatLevel DESC, i.processedAt DESC
            """)
    List<AgentInsight> findHighThreat(int minLevel);
}
