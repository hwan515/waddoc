ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS approval_requested_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS approved_by_user_id BIGINT REFERENCES "user"(user_id),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'patient_guardian_link'
          AND column_name = 'linked_at'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'patient_guardian_link'
          AND column_name = 'requested_at'
    ) THEN
        ALTER TABLE patient_guardian_link RENAME COLUMN linked_at TO requested_at;
    END IF;
END $$;

ALTER TABLE patient_guardian_link
    ADD COLUMN IF NOT EXISTS approved_by_user_id BIGINT REFERENCES "user"(user_id),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

UPDATE "user" u
SET approval_status = CASE
    WHEN u.role IN ('ADMIN', 'DOCTOR') THEN 'APPROVED'
    WHEN EXISTS (
        SELECT 1
        FROM patient_guardian_link pgl
        WHERE pgl.guardian_user_id = u.user_id
    ) THEN 'APPROVED'
    ELSE 'PENDING'
END
WHERE approval_status IS NULL;

UPDATE "user"
SET approval_requested_at = COALESCE(approval_requested_at, created_at, NOW())
WHERE approval_requested_at IS NULL;

UPDATE "user"
SET approved_at = COALESCE(approved_at, approval_requested_at)
WHERE approval_status = 'APPROVED'
  AND approved_at IS NULL;

UPDATE "user"
SET is_active = TRUE
WHERE role IN ('ADMIN', 'DOCTOR');

UPDATE "user"
SET is_active = (approval_status = 'APPROVED')
WHERE role = 'GUARDIAN';

ALTER TABLE "user"
    ALTER COLUMN approval_status SET DEFAULT 'PENDING',
    ALTER COLUMN approval_status SET NOT NULL,
    ALTER COLUMN approval_requested_at SET DEFAULT NOW(),
    ALTER COLUMN approval_requested_at SET NOT NULL;

UPDATE patient_guardian_link
SET status = COALESCE(status, 'APPROVED');

UPDATE patient_guardian_link
SET approved_at = COALESCE(approved_at, requested_at)
WHERE status = 'APPROVED'
  AND approved_at IS NULL;

ALTER TABLE patient_guardian_link
    ALTER COLUMN status SET DEFAULT 'PENDING',
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN requested_at SET DEFAULT NOW(),
    ALTER COLUMN requested_at SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_patient_guardian_link_pair'
    ) THEN
        ALTER TABLE patient_guardian_link
            ADD CONSTRAINT uq_patient_guardian_link_pair UNIQUE (patient_id, guardian_user_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_approval_status ON "user"(approval_status);
CREATE INDEX IF NOT EXISTS idx_user_approved_by ON "user"(approved_by_user_id);
CREATE INDEX IF NOT EXISTS idx_patient_guardian_link_status ON patient_guardian_link(status);
CREATE INDEX IF NOT EXISTS idx_patient_guardian_link_approver ON patient_guardian_link(approved_by_user_id);
