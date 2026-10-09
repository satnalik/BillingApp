package com.pahal.billingApp.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Hibernate update adds columns but does not expand existing enum check constraints. */
@Component
@ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "update")
public class StockMovementSchemaUpgrade implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public StockMovementSchemaUpgrade(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        Boolean postgres = jdbc.execute((ConnectionCallback<Boolean>) c ->
                "PostgreSQL".equals(c.getMetaData().getDatabaseProductName()));
        if (!Boolean.TRUE.equals(postgres)) return;
        jdbc.execute("""
                DO $$ DECLARE old_check record; BEGIN
                  FOR old_check IN
                    SELECT c.conname FROM pg_constraint c
                    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
                    WHERE c.conrelid = 'stock_movements'::regclass AND c.contype = 'c'
                      AND cardinality(c.conkey) = 1 AND a.attname = 'type'
                      AND pg_get_constraintdef(c.oid) LIKE '%OPENING_STOCK%'
                      AND pg_get_constraintdef(c.oid) LIKE '%SALE_CANCELLED%'
                      AND pg_get_constraintdef(c.oid) NOT LIKE '%PURCHASE_RETURN%'
                  LOOP
                    EXECUTE format('ALTER TABLE stock_movements DROP CONSTRAINT %I', old_check.conname);
                    EXECUTE format('ALTER TABLE stock_movements ADD CONSTRAINT %I CHECK (type IN
                      (''BALANCE_BROUGHT_FORWARD'',''OPENING_STOCK'',''ADJUSTMENT'',''PURCHASE'',
                       ''PURCHASE_RETURN'',''PURCHASE_CANCELLED'',''SALE'',''SALE_RETURN'',''SALE_CANCELLED''))', old_check.conname);
                  END LOOP;
                END $$;
                """);
    }
}
