-- Member 4: chỉ mở rộng nội dung event_details; giữ nguyên PK/FK và lịch của member 5.
ALTER TABLE event_details
    ADD COLUMN itinerary JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN packing_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_event_itinerary_array CHECK (
        jsonb_typeof(itinerary) = 'array' AND jsonb_array_length(itinerary) <= 30),
    ADD CONSTRAINT ck_event_packing_items_array CHECK (
        jsonb_typeof(packing_items) = 'array' AND jsonb_array_length(packing_items) <= 100);
