package at.ac.tuwien.infosys.viepepc.scheduler.library.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@Import(SchedulerLibraryConfiguration.class)
@Configuration
public @interface EnableSchedulerLibraryModule {
}
