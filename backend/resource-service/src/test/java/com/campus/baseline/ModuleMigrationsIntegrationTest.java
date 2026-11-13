package com.campus.baseline;
import com.campus.common.migration.ModuleMigrations;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="CAMPUS_TEST_DB_URL",matches=".+")
class ModuleMigrationsIntegrationTest {
 @Test void independentV1HistoriesReplayAndSeedsPreserveChanges()throws Exception {
  var source=new DriverManagerDataSource(System.getenv("CAMPUS_TEST_DB_URL"),System.getenv("CAMPUS_TEST_DB_USER"),System.getenv("CAMPUS_TEST_DB_PASSWORD"));
  var jdbc=new JdbcTemplate(source);assertEquals("campus_migration_probe",jdbc.queryForObject("SELECT current_database()",String.class));
  try(var context=new GenericApplicationContext()){
   context.registerBean(DataSource.class,()->source);context.refresh();
   var runner=new ModuleMigrations(context.getBeanProvider(DataSource.class),new MockEnvironment().withProperty("campus.migrations.modules","resource,reservation").withProperty("campus.migrations.root","baseline-migrations"));
   runner.afterSingletonsInstantiated();
   assertEquals("1",jdbc.queryForObject("SELECT version FROM resource_module_meta.flyway_schema_history WHERE type='SQL' AND version IS NOT NULL",String.class));
   assertEquals("1",jdbc.queryForObject("SELECT version FROM reservation_meta.flyway_schema_history WHERE type='SQL' AND version IS NOT NULL",String.class));
   jdbc.update("UPDATE resource.module_probe SET value='human-change' WHERE id=1");
   runner.afterSingletonsInstantiated();
   try(var in=getClass().getResourceAsStream("/baseline-migrations/resource/seed/R__probe.sql")){jdbc.execute(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   assertEquals("human-change",jdbc.queryForObject("SELECT value FROM resource.module_probe WHERE id=1",String.class));
   assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM resource.module_probe",Integer.class));
  }
 }
}
