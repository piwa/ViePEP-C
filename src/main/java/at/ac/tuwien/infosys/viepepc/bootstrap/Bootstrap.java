package at.ac.tuwien.infosys.viepepc.bootstrap;

import at.ac.tuwien.infosys.viepepc.bootstrap.containers.ContainerConfigurationsReaderImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 04/05/16.
 */
@Component
@Slf4j
public class Bootstrap implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private ContainerConfigurationsReaderImpl containerConfigurationsReader;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        containerConfigurationsReader.readContainerConfigurations();
    }

}
