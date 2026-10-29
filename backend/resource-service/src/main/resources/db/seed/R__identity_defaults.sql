INSERT INTO iam.permissions(code,name,module) VALUES
    ('identity:read','查看本人身份','identity'),
    ('identity:users:read','查看用户与角色','identity'),
    ('identity:users:update','修改用户权限','identity')
ON CONFLICT (code) DO NOTHING;

DO $$
DECLARE
    role_code text;
    role_name text;
    new_role_id uuid;
BEGIN
    FOR role_code,role_name IN SELECT * FROM (VALUES
        ('USER','普通用户'),('SYSTEM_ADMIN','系统管理员'),('MAINTAINER','维护人员')) AS defaults(code,name)
    LOOP
        new_role_id := NULL;
        INSERT INTO iam.roles(code,name) VALUES(role_code,role_name)
        ON CONFLICT(code) DO NOTHING RETURNING id INTO new_role_id;
        IF new_role_id IS NOT NULL THEN
            INSERT INTO iam.role_permissions(role_id,permission_id)
            SELECT new_role_id,id FROM iam.permissions
            WHERE code='identity:read' OR (role_code='SYSTEM_ADMIN' AND code IN ('identity:users:read','identity:users:update'));
        END IF;
    END LOOP;
END $$;
