package at.ac.tuwien.infosys.viepepc.database.bootstrap;

import at.ac.tuwien.infosys.viepepc.database.bootstrap.containers.ContainerConfigurationsReader;
import at.ac.tuwien.infosys.viepepc.database.bootstrap.vmTypes.VmTypesReaderImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
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
public class BootstrapDatabase implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private ContainerConfigurationsReader containerConfigurationsReader;
    @Autowired
    private VmTypesReaderImpl vmTypesReader;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheContainerService cacheDockerService;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        containerConfigurationsReader.readContainerConfigurations();
        vmTypesReader.readVMTypes();

        cacheDockerService.initializeDockerContainers();
//        cacheVirtualMachineService.initializeVMs();
    }

}
