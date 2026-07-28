-- ADR-0042 template integration: templates carry a *default* type to pre-fill when
-- creating a job from them. Project-scoped templates reference an actual job_types
-- row directly; org-scoped templates span multiple projects with different type
-- vocabularies, so they store a plain name instead, matched by name against the
-- target project's types at template-use time.
ALTER TABLE job_templates
    ADD COLUMN default_type_id UUID REFERENCES job_types(id) ON DELETE SET NULL;

ALTER TABLE job_templates
    ADD COLUMN default_type_name VARCHAR(100);
