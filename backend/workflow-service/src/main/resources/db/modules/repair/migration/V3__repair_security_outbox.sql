ALTER TABLE m3.repair_tasks ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE m3.repair_tasks ADD CONSTRAINT repair_task_kind_v3 CHECK(kind IN ('AUDIT','NOTICE','CANDIDATES','RESOURCE','M1','M2')), ADD CONSTRAINT repair_task_source_v3 CHECK(order_id IS NOT NULL OR kind='AUDIT');
