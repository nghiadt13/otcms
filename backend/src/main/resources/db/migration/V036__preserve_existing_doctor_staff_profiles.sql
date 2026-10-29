-- Directory visibility is separate from whether the employee profile is active.
UPDATE staff_profiles s
SET status = 'ACTIVE'
FROM doctor_profiles d
WHERE d.id = s.id
  AND s.status = 'REMOVED';
