-- Raw news harvested by the Java Harvester service
CREATE TABLE competitor_news (
    id BIGSERIAL PRIMARY KEY,
    competitor_name VARCHAR(100) NOT NULL,
    title TEXT NOT NULL,
    content TEXT,
    source_url TEXT,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- AI-processed strategic insights (Spring AI agent output)
CREATE TABLE agent_insights (
    id BIGSERIAL PRIMARY KEY,
    news_id BIGINT REFERENCES competitor_news(id) ON DELETE CASCADE,
    agent_name VARCHAR(50) NOT NULL,
    category VARCHAR(50),
    threat_level INTEGER CHECK (threat_level BETWEEN 1 AND 10),
    summary TEXT,
    strategic_advice TEXT,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_competitor_name ON competitor_news(competitor_name);
CREATE INDEX idx_news_published_at ON competitor_news(published_at DESC);
CREATE INDEX idx_insights_news_id ON agent_insights(news_id);
CREATE INDEX idx_insights_threat_level ON agent_insights(threat_level DESC);
