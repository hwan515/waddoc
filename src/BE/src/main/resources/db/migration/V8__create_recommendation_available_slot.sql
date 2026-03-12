CREATE TABLE recommendation_available_slot (
    id                  BIGSERIAL PRIMARY KEY,
    recommendation_id   BIGINT NOT NULL REFERENCES recommendation(recommendation_id),
    slot_id             BIGINT NOT NULL REFERENCES schedule_slot(slot_id),
    UNIQUE (recommendation_id, slot_id)
);
CREATE INDEX idx_rec_slot_recommendation ON recommendation_available_slot(recommendation_id);
