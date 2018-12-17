package at.ac.tuwien.infosys.viepepc.cloudcontroller.configuration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration
@PropertySources({
        @PropertySource("classpath:cloud-config/viepep4.0.properties"),
        @PropertySource("classpath:application_cloud_controller.properties"),
        @PropertySource("classpath:container-config/container.properties")
})
public class CloudControllerConfiguration {
}
