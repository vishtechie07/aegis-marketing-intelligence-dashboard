package com.aegis.harvester;

import com.aegis.entity.CompetitorNews;
import com.aegis.repository.CompetitorNewsRepository;
import com.aegis.service.AgentOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "DataFlowIssue"})
class HarvesterSupportTest {

    @Mock CompetitorNewsRepository newsRepository;
    @Mock AgentOrchestrationService orchestrationService;

    HarvesterSupport support;

    @BeforeEach
    void setUp() {
        support = new HarvesterSupport(newsRepository, orchestrationService);
    }

    @Test
    void saveAndDispatch_savesNewArticleAndReturnsTrue() {
        when(newsRepository.existsBySourceUrl(any())).thenReturn(false);
        when(newsRepository.save(any())).thenAnswer(inv -> {
            CompetitorNews n = inv.getArgument(0);
            // simulate DB-assigned ID
            return CompetitorNews.builder()
                    .id(1L)
                    .competitorName(n.getCompetitorName())
                    .title(n.getTitle())
                    .content(n.getContent())
                    .sourceUrl(n.getSourceUrl())
                    .publishedAt(n.getPublishedAt())
                    .sourceType(n.getSourceType())
                    .build();
        });

        boolean result = support.saveAndDispatch(
                "OpenAI", "GPT-5 Released", "Content here",
                "https://techcrunch.com/gpt5", OffsetDateTime.now(), "RSS");

        assertThat(result).isTrue();
        verify(newsRepository).save(any());
        verify(orchestrationService).processAsync(any(), eq(1L));
    }

    @Test
    void saveAndDispatch_skipsDuplicateUrl() {
        when(newsRepository.existsBySourceUrl("https://existing.com/article")).thenReturn(true);

        boolean result = support.saveAndDispatch(
                "Acme", "Old news", "content",
                "https://existing.com/article", OffsetDateTime.now(), "GDELT");

        assertThat(result).isFalse();
        verify(newsRepository, never()).save(any());
        verify(orchestrationService, never()).processAsync(any(), any());
    }

    @Test
    void saveAndDispatch_skipsNullUrl() {
        boolean result = support.saveAndDispatch("Acme", "Title", "content", null, OffsetDateTime.now(), "RSS");

        assertThat(result).isFalse();
        verifyNoInteractions(newsRepository, orchestrationService);
    }

    @Test
    void saveAndDispatch_skipsNullTitle() {
        boolean result = support.saveAndDispatch("Acme", null, "content", "https://url.com", OffsetDateTime.now(), "RSS");

        assertThat(result).isFalse();
        verifyNoInteractions(newsRepository, orchestrationService);
    }

    @Test
    void saveAndDispatch_setsSourceTypeCorrectly() {
        when(newsRepository.existsBySourceUrl(any())).thenReturn(false);
        ArgumentCaptor<CompetitorNews> captor = ArgumentCaptor.forClass(CompetitorNews.class);
        when(newsRepository.save(captor.capture())).thenAnswer(inv -> {
            CompetitorNews n = inv.getArgument(0);
            return CompetitorNews.builder().id(2L).competitorName(n.getCompetitorName())
                    .title(n.getTitle()).sourceType(n.getSourceType()).build();
        });

        support.saveAndDispatch("GitHub", "New repo push", null, "https://github.com/acme/repo",
                OffsetDateTime.now(), "GITHUB");

        assertThat(captor.getValue().getSourceType()).isEqualTo("GITHUB");
    }
}
