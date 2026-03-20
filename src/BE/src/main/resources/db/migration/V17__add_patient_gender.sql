ALTER TABLE patient
    ADD COLUMN gender VARCHAR(20);

UPDATE patient
   SET gender = 'UNKNOWN'
 WHERE gender IS NULL;

ALTER TABLE patient
    ALTER COLUMN gender SET DEFAULT 'UNKNOWN';

ALTER TABLE patient
    ALTER COLUMN gender SET NOT NULL;

ALTER TABLE patient
    ADD CONSTRAINT chk_patient_gender
        CHECK (gender IN ('MALE', 'FEMALE', 'UNKNOWN'));

COMMENT ON COLUMN patient.gender IS '환자 성별 (MALE | FEMALE | UNKNOWN)';
