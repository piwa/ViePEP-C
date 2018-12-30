package at.ac.tuwien.infosys.viepepc.cloudcontroller.configuration;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.ActionExecutorUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 05/04/2017.
 */
@Slf4j
@Component
public class StopEventHandler implements ApplicationListener<ContextClosedEvent> {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private ActionExecutorUtilities actionExecutorUtilities;
    @Autowired
    private CacheContainerService cacheContainerService;

    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Stop all running Containers...");
        for (Container container : cacheContainerService.getDeployingAndDeployedContainers()) {
            actionExecutorUtilities.stopContainer(container);
        }
        log.info("All Containers stopped");

        log.info("Stop all running VMs...");
        for (VirtualMachineInstance vm : cacheVirtualMachineService.getDeployingAndDeployedVMInstances()) {
            actionExecutorUtilities.terminateVM(vm);
        }
        log.info("All VMs stopped");
    }


}