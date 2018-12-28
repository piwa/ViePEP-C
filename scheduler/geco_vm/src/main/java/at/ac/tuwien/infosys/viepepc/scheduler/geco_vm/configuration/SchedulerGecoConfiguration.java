package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;


@Configuration
@PropertySources({
        @PropertySource("classpath:application_scheduler_geco_vm.properties"),
})
public class SchedulerGecoConfiguration {
}
