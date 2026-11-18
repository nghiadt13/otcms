-- N2: mô tả chấn thương (UC-37, BR-39) và hướng điều trị (UC-42, BR-40) của lượt khám chỉnh hình.
-- Vị trí, bên và cơ chế dùng lại các cột của V073 (injury_body_site, injury_side, injury_mechanism); không thêm cột JSON.
-- Bản ghi cũ: form cũ ghi bên bằng nhãn tiếng Việt (Trái, Phải, Hai bên) hoặc chuỗi rỗng ("Không ghi").
-- Nhãn được đổi sang mã BR-39; mọi giá trị khác không nhận dạng được được đặt NULL.
ALTER TABLE clinical_examinations
    ADD COLUMN injury_description TEXT,
    ADD COLUMN treatment_directions text[];

UPDATE clinical_examinations
SET injury_side = CASE lower(btrim(injury_side))
    WHEN 'trái' THEN 'LEFT'
    WHEN 'left' THEN 'LEFT'
    WHEN 'phải' THEN 'RIGHT'
    WHEN 'right' THEN 'RIGHT'
    WHEN 'hai bên' THEN 'BOTH'
    WHEN 'both' THEN 'BOTH'
    WHEN 'không áp dụng' THEN 'NOT_APPLICABLE'
    WHEN 'not_applicable' THEN 'NOT_APPLICABLE'
    ELSE NULL
END
WHERE injury_side IS NOT NULL;

ALTER TABLE clinical_examinations
    ADD CONSTRAINT ck_exam_injury_side CHECK (
        injury_side IS NULL OR injury_side IN ('LEFT', 'RIGHT', 'BOTH', 'NOT_APPLICABLE')
    ),
    ADD CONSTRAINT ck_exam_treatment_directions CHECK (
        treatment_directions IS NULL
        OR (cardinality(treatment_directions) BETWEEN 1 AND 4
            AND array_position(treatment_directions, NULL) IS NULL
            AND treatment_directions <@ ARRAY['HOME_CARE', 'MEDICATION', 'PHYSICAL_THERAPY', 'PROCEDURE_ELSEWHERE']::text[]
            AND cardinality(array_positions(treatment_directions, 'HOME_CARE')) <= 1
            AND cardinality(array_positions(treatment_directions, 'MEDICATION')) <= 1
            AND cardinality(array_positions(treatment_directions, 'PHYSICAL_THERAPY')) <= 1
            AND cardinality(array_positions(treatment_directions, 'PROCEDURE_ELSEWHERE')) <= 1)
    );
