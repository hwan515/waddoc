-- Backfill existing locally-seeded patients that were migrated with UNKNOWN gender.
-- Seed patient phones are generated as 01051000000 ~ 01051000079 and gender alternates by index:
-- even index -> FEMALE, odd index -> MALE.
UPDATE patient
   SET gender = CASE
                    WHEN (CAST(RIGHT(phone, 8) AS INTEGER) - 51000000) % 2 = 0 THEN 'FEMALE'
                    ELSE 'MALE'
                END
 WHERE gender = 'UNKNOWN'
   AND phone ~ '^010[0-9]{8}$'
   AND CAST(RIGHT(phone, 8) AS INTEGER) BETWEEN 51000000 AND 51000079;
