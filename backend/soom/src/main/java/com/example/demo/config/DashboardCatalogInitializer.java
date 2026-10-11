package com.example.demo.config;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
public class DashboardCatalogInitializer implements ApplicationRunner {

    private final DataSource source;

    public DashboardCatalogInitializer(DataSource source) {
        this.source = source;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean travel = false,
            fighting = false,
            comments = false;
        try (
            var connection = source.getConnection();
            var tables = connection
                .getMetaData()
                .getTables(connection.getCatalog(), null, "%", new String[] { "TABLE" })
        ) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                travel |= name.equalsIgnoreCase("no_smoking_travel");
                fighting |= name.equalsIgnoreCase("fighting");
                comments |= name.equalsIgnoreCase("comment");
            }
        }
        if (comments) new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(source)
        ).executeWithoutResult(status ->
            com.example.demo.service.CommentThreadMigration.flattenExisting(
                new org.springframework.jdbc.core.JdbcTemplate(source)
            )
        );
        if (travel && fighting) {
            var populator = new ResourceDatabasePopulator(
                new ClassPathResource("dashboard-catalog.sql")
            );
            populator.setSqlScriptEncoding("UTF-8");
            populator.execute(source);
        }
    }
}
