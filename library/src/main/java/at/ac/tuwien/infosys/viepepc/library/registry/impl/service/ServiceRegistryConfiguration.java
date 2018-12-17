package at.ac.tuwien.infosys.viepepc.library.registry.impl.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Created by philippwaibel on 18/10/2016.
 */
@Slf4j
@Configuration
@PropertySource(value = "classpath:application_library.properties")
public class ServiceRegistryConfiguration {
}
