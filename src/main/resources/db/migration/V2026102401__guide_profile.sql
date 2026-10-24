-- Member 4: bổ sung hồ sơ guide; giữ nguyên PK guide_id và tham chiếu user_id.
-- Hồ sơ cũ có bio/ảnh null và languages rỗng; không thay đổi trạng thái/ID.
ALTER TABLE tour_guides
    ADD COLUMN bio TEXT,
    ADD COLUMN languages TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN avatar_url TEXT,
    ADD COLUMN avatar_public_id TEXT;

ALTER TABLE tour_guides
    ADD CONSTRAINT ck_guide_bio_length CHECK (bio IS NULL OR length(bio) <= 5000),
    ADD CONSTRAINT ck_guide_languages_count CHECK (cardinality(languages) <= 10),
    ADD CONSTRAINT ck_guide_avatar_pair CHECK (
        (avatar_url IS NULL AND avatar_public_id IS NULL)
        OR (avatar_url IS NOT NULL AND avatar_public_id IS NOT NULL)
    );
