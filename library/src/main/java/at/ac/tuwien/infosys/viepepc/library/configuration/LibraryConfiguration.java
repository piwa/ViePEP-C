package at.ac.tuwien.infosys.viepepc.library.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration
@PropertySources({
        @PropertySource("classpath:container-config/container.properties"),
        @PropertySource("classpath:application_library.properties")
})
public class LibraryConfiguration {
}
