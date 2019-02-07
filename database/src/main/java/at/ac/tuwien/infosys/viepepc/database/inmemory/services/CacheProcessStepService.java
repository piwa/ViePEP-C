package at.ac.tuwien.infosys.viepepc.database.inmemory.services;


import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStepStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Component
public class CacheProcessStepService {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;


    public synchronized List<ProcessStep> getScheduledProcessSteps() {
        return inMemoryCache.getAllProcessSteps().values().stream().filter(ps -> ps.getProcessStepStatus().equals(ProcessStepStatus.SCHEDULED)).collect(Collectors.toList());
    }

    public synchronized List<ProcessStep> getDeployingProcessSteps() {
        return inMemoryCache.getAllProcessSteps().values().stream().filter(ps -> ps.getProcessStepStatus().equals(ProcessStepStatus.DEPLOYING)).collect(Collectors.toList());
    }

    public synchronized List<ProcessStep> getRunningProcessSteps() {
        return inMemoryCache.getAllProcessSteps().values().stream().filter(ps -> ps.getProcessStepStatus().equals(ProcessStepStatus.RUNNING)).collect(Collectors.toList());
    }

    public synchronized List<Container> getTerminatedContainers() {
        return inMemoryCache.getContainerInstances().values().stream().filter(container -> container.getContainerStatus().equals(VirtualMachineStatus.TERMINATED)).collect(Collectors.toList());
    }

    public synchronized Optional<ProcessStep> getRunningProcessStep(String processStepName) {
        return inMemoryCache.getAllProcessSteps().values().stream().filter(ps -> ps.getProcessStepStatus().equals(ProcessStepStatus.RUNNING) && ps.getName().equals(processStepName)).findFirst();
    }


    public List<ProcessStep> findByContainerAndRunning(Container container) {
        return getRunningProcessSteps()
                .stream().filter(processStep -> processStep.getContainer() == container && processStep.getFinishedAt() == null)
                .collect(Collectors.toList());
    }

    public Map<UUID, ProcessStep> getAllProcessSteps() {
        return inMemoryCache.getAllProcessSteps();
    }

//    public ConcurrentMap<String, ProcessStep> getProcessStepsWaitingForServiceDone() {
//        return inMemoryCache.getProcessStepsWaitingForServiceDone();
//    }

//    public Set<ProcessStep> getProcessStepsWaitingForExecution() {
//        return inMemoryCache.getProcessStepsWaitingForExecution();
//    }
}
