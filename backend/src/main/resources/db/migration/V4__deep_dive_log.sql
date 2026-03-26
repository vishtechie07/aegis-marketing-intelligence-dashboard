CREATE TABLE deep_dive_log (
    id BIGSERIAL PRIMARY KEY,
    news_id BIGINT NOT NULL REFERENCES competitor_news(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    analysis TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_deep_dive_news_created ON deep_dive_log(news_id, created_at DESC);
