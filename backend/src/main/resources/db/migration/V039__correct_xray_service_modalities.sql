UPDATE clinic_services
SET modality = 'XRAY'
WHERE service_type = 'IMAGING'
  AND (lower(name) LIKE '%x-quang%' OR lower(name) LIKE '%x quang%');
