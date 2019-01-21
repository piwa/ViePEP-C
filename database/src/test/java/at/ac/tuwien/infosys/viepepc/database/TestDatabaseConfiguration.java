package at.ac.tuwien.infosys.viepepc.database;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;


@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "at.ac.tuwien.infosys.viepepc")
@EnableRetry
@EnableScheduling
@EnableAsync
@TestPropertySource(locations = {
        "classpath:application_scheduler_geco_vm.properties",
        "classpath:database-config/mysql.properties",
        "classpath:application_database.properties",
        "classpath:application_library.properties",
        "classpath:cloud-config/viepep4.0.properties",
        "classpath:application_cloud_controller.properties",
        "classpath:container-config/container.properties",
}, properties = {"evaluation.prefix=1", "profile.specific.database.name=geco_vm", "evaluation.suffix=1"})
@Profile("test")
public class TestDatabaseConfiguration {

    @Bean
    @Primary
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(5);
        executor.initialize();
        return executor;
    }

}