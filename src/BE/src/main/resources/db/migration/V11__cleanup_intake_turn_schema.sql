ALTER TABLE intake_turn
  DROP COLUMN IF EXISTS dtmf_input;

COMMENT ON COLUMN intake_turn.turn_type IS '입력 유형 (VOICE)';
