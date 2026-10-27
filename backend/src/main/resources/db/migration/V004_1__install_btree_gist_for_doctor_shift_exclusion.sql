-- V005 creates a GiST exclusion constraint combining UUID equality and
-- timestamp range overlap. Install the UUID GiST operator classes first.
-- Kept idempotent because V013 also declares this extension prerequisite.
CREATE EXTENSION IF NOT EXISTS btree_gist;
