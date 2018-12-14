package at.ac.tuwien.infosys.viepepc.cloudcontroller.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration
@PropertySources({
        @PropertySource("classpath:cloud-config/viepep4.0.properties"),
        @PropertySource("classpath:application.properties"),
        @PropertySource("classpath:container-config/container.properties")
})
public class CloudControllerConfiguration {
}
