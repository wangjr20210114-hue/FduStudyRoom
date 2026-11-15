INSERT INTO iam.permissions(code,name,module) VALUES
 ('identity:roles:read','Read roles and permissions','identity'),
 ('identity:roles:write','Configure roles and permissions','identity'),
 ('resource:rooms:read','Read rooms','resource'),
 ('resource:rooms:write','Manage rooms','resource'),
 ('resource:checkin-codes:read','Read check-in codes','resource'),
 ('reservation:read','Read own reservations','reservation'),
 ('approval:read','Read classroom approvals','approval'),
 ('approval:decide','Decide classroom approvals','approval'),
 ('repair:history:read','Read repair history','repair'),
 ('repair:accept','Accept completed repairs','repair'),
 ('repair:evaluate','Evaluate completed repairs','repair'),
 ('notification:read','Read own notifications','notification')
ON CONFLICT(code) DO NOTHING;

INSERT INTO iam.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM iam.roles r CROSS JOIN iam.permissions p
WHERE r.code='SYSTEM_ADMIN'
ON CONFLICT(role_id,permission_id) DO NOTHING;

INSERT INTO iam.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM iam.roles r JOIN iam.permissions p ON p.code='notification:read'
WHERE r.code='USER'
ON CONFLICT(role_id,permission_id) DO NOTHING;
