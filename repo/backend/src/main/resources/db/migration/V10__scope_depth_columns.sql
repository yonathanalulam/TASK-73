-- V10: Add department_id and facility_area_id columns to users and students
-- to support sub-organization scope filtering (department/facility level).

ALTER TABLE users ADD COLUMN department_id BIGINT NULL;
ALTER TABLE users ADD COLUMN facility_area_id BIGINT NULL;
CREATE INDEX idx_users_department ON users (department_id);
CREATE INDEX idx_users_facility_area ON users (facility_area_id);

ALTER TABLE students ADD COLUMN department_id BIGINT NULL;
ALTER TABLE students ADD COLUMN facility_area_id BIGINT NULL;
CREATE INDEX idx_students_department ON students (department_id);
CREATE INDEX idx_students_facility_area ON students (facility_area_id);

ALTER TABLE training_sessions ADD COLUMN department_id BIGINT NULL;
CREATE INDEX idx_training_sessions_department ON training_sessions (department_id);
