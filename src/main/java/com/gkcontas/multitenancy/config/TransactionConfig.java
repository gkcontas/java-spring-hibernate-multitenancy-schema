package com.gkcontas.multitenancy.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One template per persistence unit. The scenarios use them directly rather
 * than through {@code @Transactional}, for two reasons: several variants have
 * to run on a thread the scenario started itself, where a proxy would not be
 * involved, and several have to end in a rollback so the measurement does not
 * change the dataset the next variant reads.
 */
@Configuration
public class TransactionConfig {

    @Bean
    public TransactionTemplate schemaTransactions(
            @Qualifier("schemaTransactionManager") PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    public TransactionTemplate sharedTransactions(
            @Qualifier("sharedTransactionManager") PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}
