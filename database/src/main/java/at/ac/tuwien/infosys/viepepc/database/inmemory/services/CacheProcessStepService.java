package at.ac.tuwien.infosys.viepepc.database.inmemory.services;


import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Component
public class CacheProcessStepService {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;


    public List<ProcessStep> findByContainerAndRunning(Container container) {
        return inMemoryCache.getProcessStepsWaitingForServiceDone().values()
                .stream().filter(processStep -> processStep.getContainer() == container && processStep.getFinishedAt() == null)
                .collect(Collectors.toList());
    }

    public ConcurrentMap<String, ProcessStep> getProcessStepsWaitingForServiceDone() {
        return inMemoryCache.getProcessStepsWaitingForServiceDone();
    }

    public Set<ProcessStep> getWaitingForExecutingProcessSteps() {
        return inMemoryCache.getWaitingForExecutingProcessSteps();
    }
}
