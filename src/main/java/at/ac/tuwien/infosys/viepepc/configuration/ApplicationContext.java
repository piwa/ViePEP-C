package at.ac.tuwien.infosys.viepepc.configuration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Created by philippwaibel on 03/05/16.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(value="at.ac.tuwien.infosys.viepepc")
@EnableRetry
@EnableScheduling
@EnableAsync
@PropertySources({
        @PropertySource("classpath:container-config/container.properties"),
        @PropertySource("classpath:database-config/mysql.properties"),
        @PropertySource("classpath:application.properties"),
        @PropertySource("classpath:application-container.properties")
})
public class ApplicationContext  {

    @Bean
    @Primary
    public SimpleAsyncTaskExecutor simpleAsyncTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setConcurrencyLimit(200);
        return executor;
    }

    @Bean(name = "serviceProcessExecuter")
    public ThreadPoolTaskExecutor serviceProcessExecuter() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(150);
        executor.setCorePoolSize(100);
        executor.setQueueCapacity(50);
        executor.initialize();
        return executor;
    }

}
