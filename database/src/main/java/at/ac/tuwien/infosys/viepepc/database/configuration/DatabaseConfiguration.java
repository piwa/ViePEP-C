package at.ac.tuwien.infosys.viepepc.database.configuration;

import at.ac.tuwien.infosys.viepepc.library.configuration.EnableLibraryModule;
import org.springframework.boot.autoconfigure.domain.EntityScan;
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
@EntityScan(basePackages = "at.ac.tuwien.infosys.viepepc.library.entities")
@EnableTransactionManagement
@PropertySources({
        @PropertySource("classpath:database-config/mysql.properties"),
        @PropertySource("classpath:application_database.properties")
})
//@EnableLibraryModule
public class DatabaseConfiguration {
}
