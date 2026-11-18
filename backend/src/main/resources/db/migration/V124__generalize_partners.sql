-- V123 has already been applied. Preserve its partner records and the laboratory sample FK.
ALTER TABLE laboratory_partners RENAME TO partners;
ALTER INDEX uq_laboratory_partner_name RENAME TO uq_partner_name;
ALTER TABLE laboratory_samples RENAME CONSTRAINT laboratory_samples_partner_id_fkey TO fk_laboratory_samples_partner;

CREATE TABLE partner_categories (
    id uuid PRIMARY KEY,
    code varchar(32) NOT NULL UNIQUE CHECK (length(btrim(code)) > 0),
    name varchar(100) NOT NULL CHECK (length(btrim(name)) > 0),
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE partner_category_assignments (
    partner_id uuid NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    category_id uuid NOT NULL REFERENCES partner_categories(id) ON DELETE RESTRICT,
    PRIMARY KEY (partner_id, category_id)
);
CREATE INDEX ix_partner_category_assignments_category ON partner_category_assignments(category_id, partner_id);

INSERT INTO partner_categories (id, code, name) VALUES
    ('12400000-0000-0000-0000-000000000001', 'LABORATORY', 'Xét nghiệm'),
    ('12400000-0000-0000-0000-000000000002', 'IMAGING', 'Chẩn đoán hình ảnh'),
    ('12400000-0000-0000-0000-000000000003', 'PHARMACY', 'Dược phẩm'),
    ('12400000-0000-0000-0000-000000000004', 'OTHER', 'Khác');

INSERT INTO partner_category_assignments (partner_id, category_id)
SELECT p.id, c.id FROM partners p CROSS JOIN partner_categories c WHERE c.code = 'LABORATORY';
