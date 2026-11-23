-- Object storage phase (plan 261122-2307): file content moves from bytea columns to Supabase Storage / local storage.
-- This step only adds the keys and relaxes the bytea constraints; the bytea columns are dropped in V145.
-- medical_documents needs no key column: its object key is derived as medical-documents/<visit_id>/<id>.

-- Decision Q4: old files are not kept, so legacy avatar/photo metadata is cleared before the new constraints
-- (rows with a content type but no storage key would violate them). Bytea content is dropped in V145.
UPDATE accounts SET avatar_content = NULL, avatar_content_type = NULL, avatar_updated_at = NULL WHERE avatar_content_type IS NOT NULL;
UPDATE staff_profiles SET photo_content = NULL, photo_content_type = NULL WHERE photo_content_type IS NOT NULL;

-- accounts: avatar is complete when key, content type and updated_at are all set or all null.
ALTER TABLE accounts ADD COLUMN avatar_storage_key varchar(300);
ALTER TABLE accounts DROP CONSTRAINT ck_accounts_avatar_complete;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_avatar_complete CHECK (
    ((avatar_storage_key IS NULL) = (avatar_content_type IS NULL))
    AND ((avatar_storage_key IS NULL) = (avatar_updated_at IS NULL)));

-- staff_profiles: photo is complete when key and content type are both set or both null.
ALTER TABLE staff_profiles ADD COLUMN photo_storage_key varchar(300);
ALTER TABLE staff_profiles DROP CONSTRAINT ck_staff_profile_photo;
ALTER TABLE staff_profiles ADD CONSTRAINT ck_staff_profile_photo CHECK (
    (photo_storage_key IS NULL AND photo_content_type IS NULL)
    OR (photo_storage_key IS NOT NULL AND photo_content_type IN ('image/jpeg', 'image/png', 'image/webp')));

-- website_images: content is no longer written; size is kept for validation and display.
ALTER TABLE website_images ADD COLUMN storage_key varchar(300);
ALTER TABLE website_images ADD COLUMN byte_size bigint;
ALTER TABLE website_images ALTER COLUMN content DROP NOT NULL;
ALTER TABLE website_images ADD CONSTRAINT ck_website_images_byte_size CHECK (byte_size IS NULL OR byte_size BETWEEN 1 AND 2097152);

-- clinical_medical_document_payloads: content is no longer written.
ALTER TABLE clinical_medical_document_payloads ALTER COLUMN content DROP NOT NULL;
