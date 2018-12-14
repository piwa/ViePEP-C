package at.ac.tuwien.infosys.viepepc.database.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Created by philippwaibel on 10/03/16.
 */
@Configuration
@EnableJpaRepositories(basePackages = {"at.ac.tuwien.infosys.viepepc.database.externdb.repositories"})
@EnableTransactionManagement
@PropertySources({
        @PropertySource("classpath:database-config/mysql.properties"),
        @PropertySource("classpath:application.properties")
})
public class DatabaseConfiguration {
}
