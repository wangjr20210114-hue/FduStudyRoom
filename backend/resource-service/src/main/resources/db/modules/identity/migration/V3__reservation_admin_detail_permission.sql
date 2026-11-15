INSERT INTO iam.permissions(code,name,module) VALUES ('reservation:admin-read','Read administrative reservation details','reservation') ON CONFLICT(code) DO NOTHING;
INSERT INTO iam.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM iam.roles r JOIN iam.permissions p ON p.code='reservation:admin-read'
WHERE r.code='SYSTEM_ADMIN' ON CONFLICT(role_id,permission_id) DO NOTHING;
