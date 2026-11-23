-- Object storage phase (plan 261122-2307): content now lives in object storage, so the bytea columns go.
-- Check constraints that mention a dropped column (ck_accounts_avatar_size, website_images size check) are dropped with it.
-- Back up the database first: dropped content cannot be recovered (no backfill, decision Q4 = do not keep old files).
ALTER TABLE accounts DROP COLUMN avatar_content;
ALTER TABLE staff_profiles DROP COLUMN photo_content;
ALTER TABLE website_images DROP COLUMN content;
DROP TABLE clinical_medical_document_payloads;
