-- intake_turn에 public_id 추가
ALTER TABLE intake_turn
  ADD COLUMN public_id VARCHAR(20);

-- 기존 행 백필: 랜덤 8자 alphanumeric 백필 후 unique 제약으로 보장
DO $$
DECLARE
    r RECORD;
    chars TEXT := '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
    result TEXT;
    i INT;
BEGIN
    FOR r IN SELECT turn_id FROM intake_turn WHERE public_id IS NULL LOOP
        result := 'turn_';
        FOR i IN 1..8 LOOP
            result := result || substr(chars, floor(random() * 62 + 1)::int, 1);
        END LOOP;
        UPDATE intake_turn SET public_id = result WHERE turn_id = r.turn_id;
    END LOOP;
END $$;

ALTER TABLE intake_turn
  ALTER COLUMN public_id SET NOT NULL;

ALTER TABLE intake_turn
  ADD CONSTRAINT uq_intake_turn_public_id UNIQUE (public_id);

-- turnOrder 중복 방지 (세션 내 unique)
ALTER TABLE intake_turn
  ADD CONSTRAINT uq_intake_turn_session_order UNIQUE (intake_session_id, turn_order);
