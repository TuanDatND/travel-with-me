-- Member 4: thêm vị trí gallery và ngày liên quan, giữ nguyên ảnh và khóa hiện tại.
ALTER TABLE event_images
    ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN day_number INTEGER,
    ADD CONSTRAINT ck_event_image_sort CHECK (sort_order >= 0),
    ADD CONSTRAINT ck_event_image_day CHECK (day_number BETWEEN 1 AND 30);

-- Bảo toàn ảnh bìa/thứ tự cũ theo image_id; vị trí đầu tiên tiếp tục là ảnh bìa.
WITH positions AS (
    SELECT image_id, row_number() OVER (PARTITION BY event_id ORDER BY image_id) - 1 AS position
    FROM event_images
)
UPDATE event_images i SET sort_order = p.position FROM positions p WHERE i.image_id = p.image_id;

CREATE INDEX idx_event_images_layout ON event_images(event_id, sort_order, image_id);
