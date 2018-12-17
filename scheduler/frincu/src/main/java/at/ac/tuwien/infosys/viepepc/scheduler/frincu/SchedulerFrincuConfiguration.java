package at.ac.tuwien.infosys.viepepc.scheduler.core;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration
@PropertySources({
        @PropertySource("classpath:application_scheduler_frincu.properties")
})
public class SchedulerFrincuConfiguration {
}
