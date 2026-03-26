ALTER TABLE competitor_news
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) DEFAULT 'RSS';

CREATE INDEX IF NOT EXISTS idx_news_source_type ON competitor_news(source_type);
