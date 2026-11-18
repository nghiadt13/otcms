CREATE TABLE laboratory_partners (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL CHECK (length(btrim(name)) > 0),
    contact varchar(255),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_laboratory_partner_name ON laboratory_partners (lower(btrim(name)));
ALTER TABLE laboratory_samples ADD COLUMN partner_id uuid REFERENCES laboratory_partners(id) ON DELETE RESTRICT;
