package at.ac.tuwien.infosys.viepepc.configuration;

import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.OnlyContainerDeploymentController;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvoker;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvokerImpl;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvokerSimulation;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.*;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

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
        @PropertySource("classpath:application.properties"),
        @PropertySource("classpath:application-container.properties"),
        @PropertySource("classpath:application-heuristic.properties"),
        @PropertySource("classpath:container-config/container.properties"),
        @PropertySource("classpath:database-config/mysql.properties"),
        @PropertySource("classpath:cloud-config/viepep4.0.properties"),
        @PropertySource("classpath:messagebus-config/messagebus.properties"),
        @PropertySource("classpath:slack-config/slack.properties"),
        @PropertySource("classpath:simulation.properties")
})
public class ApplicationContext implements AsyncConfigurer {

    @Value("${simulate}")
    private boolean simulate;

    @Bean
    public ServiceInvoker getServiceInvoker() {
        if(simulate) {
            return new ServiceInvokerSimulation();
        }
        return new ServiceInvokerImpl();
    }

    @Bean
    @Scope("prototype")
    public OnlyContainerDeploymentController getOnlyContainerDeploymentController(ProcessStep processStep) {
        return new OnlyContainerDeploymentController(processStep);
    }

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(5);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler(){
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(100);
        threadPoolTaskScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
        return threadPoolTaskScheduler;
    }

}
