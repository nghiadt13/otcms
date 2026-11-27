-- Separate doctor working medical history from the published snapshot visible to patients after visit completion.
ALTER TABLE patient_profiles
    ADD COLUMN published_medical_history JSONB;

UPDATE patient_profiles pp
SET published_medical_history = jsonb_build_object(
    'conditions', pp.conditions,
    'allergies', pp.allergies,
    'procedures', pp.procedures,
    'injuries', pp.injuries,
    'orthopedicHistory', pp.orthopedic_history,
    'publishedAt', to_char(pp.medical_history_updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'publishedBy', pp.medical_history_updated_by::text,
    'version', pp.medical_history_version
)
WHERE pp.medical_history_updated_at IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM visits v
      WHERE v.patient_profile_id = pp.id
        AND v.status = 'CLOSED'
  );
