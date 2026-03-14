-- 환자 얼굴 기준 사진은 관리자가 직접 등록한다.
-- 승인/반려 단계 없이, 삭제되지 않은 사진 존재 여부만 본인확인 전제조건으로 사용한다.

ALTER TABLE patient_face_reference
    DROP COLUMN IF EXISTS status,
    DROP COLUMN IF EXISTS approved_by,
    DROP COLUMN IF EXISTS approved_at;

COMMENT ON TABLE patient_face_reference IS '환자 사전 등록 얼굴 사진';
COMMENT ON COLUMN patient_face_reference.public_id IS '외부 노출 ID (face_xxxx)';
COMMENT ON COLUMN patient_face_reference.photo_path IS '본인확인 기준 사진 상대경로';
COMMENT ON COLUMN patient_face_reference.uploaded_by IS '등록 관리자 (user_id)';
COMMENT ON COLUMN patient_face_reference.deleted_at IS '소프트 삭제 시각';
