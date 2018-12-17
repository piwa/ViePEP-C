package at.ac.tuwien.infosys.viepepc.scheduler.library.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration
@PropertySources({
        @PropertySource("classpath:application_scheduler_library.properties")
})
//@EnableDatabaseModule
public class SchedulerLibraryConfiguration {

}
