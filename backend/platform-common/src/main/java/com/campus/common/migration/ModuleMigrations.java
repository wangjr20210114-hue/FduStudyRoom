package com.campus.common.migration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
@Component
public final class ModuleMigrations implements SmartInitializingSingleton {
 private final ObjectProvider<DataSource> sources;private final Environment env;
 public ModuleMigrations(ObjectProvider<DataSource> sources,Environment env){this.sources=sources;this.env=env;}
 @Override public void afterSingletonsInstantiated(){
  String names=env.getProperty("campus.migrations.modules","");
  if(names.isBlank())return;
  var source=sources.getIfAvailable();if(source==null)throw new IllegalStateException("Module migrations require a datasource");
  var resolver=new PathMatchingResourcePatternResolver();
  for(String raw:names.split(",")){
   String name=raw.trim();if(!java.util.Set.of("identity","resource","reservation","approval","repair","support").contains(name))throw new IllegalStateException("Unknown migration namespace");
   String prefix=env.getProperty("campus.migrations.root","db/modules");
   if(!prefix.matches("[A-Za-z0-9_/-]+")||prefix.contains("..")||prefix.startsWith("/"))throw new IllegalStateException("Invalid migration resource root");
   String root=prefix+"/"+name+"/";
   try {
    if(resolver.getResources("classpath*:"+root+"migration/*.sql").length==0)continue;
    String[] schemas=switch(name){case "identity"->new String[]{"identity_meta","iam"};case "resource"->new String[]{"resource_module_meta","resource","integration"};case "reservation"->new String[]{"reservation_meta","m1"};case "approval"->new String[]{"approval_meta","m2"};case "repair"->new String[]{"repair_meta","m3"};default->new String[]{"support_meta","support"};};
    Flyway.configure().dataSource(source).defaultSchema(schemas[0]).schemas(schemas)
      .locations("classpath:"+root+"migration","classpath:"+root+"seed").cleanDisabled(true).load().migrate();
   } catch(java.io.IOException e){throw new IllegalStateException("Cannot discover module migrations",e);}
  }
 }
}
