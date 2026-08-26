-- 공지의 공개 여부. 관리자가 작성 중인 글이 바로 사용자에게 보이지 않게 한다.
--
-- 기존 공지는 이미 사용자에게 보이고 있었으므로 전부 공개로 둔다. 기본값을
-- FALSE로 하면 이 migration이 도는 순간 모든 공지가 사라진다.
ALTER TABLE notices
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX ix_notices_published ON notices (published, important, published_at);
