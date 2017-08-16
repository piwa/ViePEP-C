package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class Watchdog {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private ViePEPCloudService viePEPCloudServiceImpl;

//    @Scheduled(initialDelay=10000, fixedDelay=10000)        // fixedRate
    public void monitor() {
        Set<VirtualMachine> virtualMachineList = cacheVirtualMachineService.getStartedVMs();

        for(VirtualMachine vm : virtualMachineList) {
            boolean available = viePEPCloudServiceImpl.checkAvailabilityOfDockerhost(vm);

            if(!available) {

            }


        }

    }


}
