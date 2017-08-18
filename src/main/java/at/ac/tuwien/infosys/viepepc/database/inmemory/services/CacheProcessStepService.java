package at.ac.tuwien.infosys.viepepc.database.inmemory.services;


import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CacheProcessStepService {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;


    public List<ProcessStep> findByContainerAndRunning(Long containerId) {
        return inMemoryCache.getProcessStepsWaitingForServiceDone().values()
                .stream().filter(processStep -> processStep.getScheduledAtContainer().equals(containerId) && processStep.getFinishedAt() == null)
                .collect(Collectors.toList());


    }

}
