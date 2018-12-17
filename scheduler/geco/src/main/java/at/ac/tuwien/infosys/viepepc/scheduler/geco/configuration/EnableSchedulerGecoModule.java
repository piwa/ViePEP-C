package at.ac.tuwien.infosys.viepepc.scheduler.geco.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@Import(SchedulerGecoConfiguration.class)
@Configuration
public @interface EnableSchedulerGecoModule {
}
