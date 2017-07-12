package at.ac.tuwien.infosys.viepepc.configuration;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Created by philippwaibel on 05/04/2017.
 */
@Slf4j
@Component
public class StopEventHandler implements ApplicationListener<ContextClosedEvent> {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private PlacementHelper placementHelper;

    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Stop all running VMs...");

        for(VirtualMachine vm : cacheVirtualMachineService.getStartedVMs()) {
            placementHelper.terminateVM(vm);
        }

        log.info("All VMs stopped");
    }



}