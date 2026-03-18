-- Increase max length for notes and approval descriptions to support real operational use.
ALTER TABLE notes ALTER COLUMN content TYPE VARCHAR(10000);
ALTER TABLE approvals ALTER COLUMN description TYPE VARCHAR(2000);
