package com.campus.identity.infrastructure;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@ConditionalOnProperty(name = "campus.auth.mode", havingValue = "dev")
public class DevelopmentData implements ApplicationRunner {
    private final DataSource dataSource;
    public DevelopmentData(DataSource dataSource) { this.dataSource = dataSource; }
    @Override
    public void run(ApplicationArguments args) {
        new ResourceDatabasePopulator(new ClassPathResource("db/demo/identity.sql")).execute(dataSource);
    }
}
