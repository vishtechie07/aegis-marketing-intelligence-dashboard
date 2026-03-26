-- Widen source_url to support long Google News RSS redirect URLs (265–624+ chars)
ALTER TABLE competitor_news ALTER COLUMN source_url TYPE VARCHAR(2048);
