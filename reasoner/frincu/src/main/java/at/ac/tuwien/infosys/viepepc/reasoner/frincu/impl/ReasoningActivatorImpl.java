package at.ac.tuwien.infosys.viepepc.reasoner.frincu.impl;

import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.Reasoning;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.ReasoningActivator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Scope("prototype")
@Slf4j
public class ReasoningActivatorImpl implements ReasoningActivator {

    @Autowired
    protected Reasoning reasoning;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheContainerService cacheDockerService;
    
    @Value("${reasoner.autoTerminate}")
    private boolean autoTerminate;

    @Value("${use.container}")
    private boolean useDocker;

    @Override
    public void initialize() {
        log.info("ReasoningActivator initialized");
//        inMemoryCache.clear();
        if(useDocker) {
            cacheDockerService.initializeDockerContainers();
        }
        cacheVirtualMachineService.initializeVMs();
    }

    @Override
    public Future<Boolean> start() throws Exception {
    	return reasoning.runReasoning(new Date(), autoTerminate);
    }

    @Override
    public void stop() {
        reasoning.stop();
    }
}
