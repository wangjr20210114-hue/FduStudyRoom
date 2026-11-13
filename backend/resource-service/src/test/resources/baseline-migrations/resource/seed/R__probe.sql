INSERT INTO resource.module_probe(id,value) VALUES(1,'seed') ON CONFLICT(id) DO NOTHING;
