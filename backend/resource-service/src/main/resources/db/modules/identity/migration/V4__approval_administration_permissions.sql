INSERT INTO iam.permissions(code,name,module) VALUES
 ('approval:flow:read','Read classroom approval flow','approval'),
 ('approval:flow:write','Configure classroom approval flow','approval'),
 ('approval:admin-cancel','Administratively cancel classroom applications','approval')
ON CONFLICT(code) DO NOTHING;
INSERT INTO iam.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM iam.roles r JOIN iam.permissions p ON p.code IN('approval:flow:read','approval:flow:write','approval:admin-cancel')
WHERE r.code='SYSTEM_ADMIN' ON CONFLICT(role_id,permission_id) DO NOTHING;
