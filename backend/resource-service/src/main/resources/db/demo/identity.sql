INSERT INTO iam.users(id,login_name,display_name,identity_type,identity_source,identity_active,role_id,enabled)
SELECT demo.id::uuid,demo.login_name,demo.display_name,demo.identity_type,'DEVELOPMENT',demo.identity_active,r.id,demo.enabled
FROM (VALUES
    ('00000000-0000-0000-0000-000000002001','DEV-STUDENT','开发学生','STUDENT','USER',true,true),
    ('00000000-0000-0000-0000-000000002002','DEV-ADMIN','开发管理员','STAFF','SYSTEM_ADMIN',true,true),
    ('00000000-0000-0000-0000-000000002003','DEV-MAINTAINER','开发维护人员','STAFF','MAINTAINER',true,true),
    ('00000000-0000-0000-0000-000000002004','DEV-STAFF-USER','无管理授权的开发教职工','STAFF','USER',true,true),
    ('00000000-0000-0000-0000-000000002005','DEV-INACTIVE','资格无效的开发学生','STUDENT','USER',false,true),
    ('00000000-0000-0000-0000-000000002006','DEV-DISABLED','已停用的开发学生','STUDENT','USER',true,false)
) AS demo(id,login_name,display_name,identity_type,role_code,identity_active,enabled)
JOIN iam.roles r ON r.code=demo.role_code
ON CONFLICT(login_name) DO NOTHING;
