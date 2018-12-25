package at.ac.tuwien.infosys.viepepc.scheduler.library;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@Profile("OnlyContainer")
public class PrintRunningInfoOnlyContainer implements PrintRunningInfo {

    @Autowired
    private WorkflowUtilities workflowUtilities;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    @Override
    public void printRunningInformation() {
        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Running Workflows:\n");
            printRunningInformation(stringBuilder);
            stringBuilder.append("\n");

            printWaitingInformation(stringBuilder);

            log.debug(stringBuilder.toString());
        } catch (Exception ex) {
            log.error("Exception while printing running information. But is ignored :D");
        }
    }


    private void printRunningInformation(StringBuilder stringBuilder) {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        stringBuilder.append("Running Threads: " + threadSet.size() + "\n");

        stringBuilder.append("--------------------------- Containers running ---------------------------\n");
        for (Container container : inMemoryCache.getRunningContainers()) {
            if (container!= null && container.getContainerStatus().equals(ContainerStatus.DEPLOYED)) {
                stringBuilder.append(container.toString()).append("\n");
            }
        }

        stringBuilder.append("----------------------------- Tasks running ------------------------------\n");
        getRunningTasks(stringBuilder);
    }


    private void printWaitingInformation(StringBuilder stringBuilder) {
        stringBuilder.append("-------------------- Containers waiting for starting ---------------------\n");
        Set<Container> containers = inMemoryCache.getWaitingForExecutingProcessSteps().stream().map(ProcessStep::getContainer).collect(Collectors.toSet());
        for (Container container : containers) {
            if (container.getContainerStatus().equals(ContainerStatus.SCHEDULED) || container.getContainerStatus().equals(ContainerStatus.DEPLOYING)) {
                stringBuilder.append(container.toString()).append("\n");
            }
        }
        stringBuilder.append("----------------------- Tasks waiting for starting -----------------------\n");
        for (ProcessStep processStep : inMemoryCache.getWaitingForExecutingProcessSteps()) {
            stringBuilder.append(processStep.toString()).append("\n");
        }
    }


    private void getRunningTasks(StringBuilder stringBuilder) {
        List<WorkflowElement> allWorkflowInstances = cacheWorkflowService.getRunningWorkflowInstances();
        List<ProcessStep> nextSteps = workflowUtilities.getNotStartedUnfinishedSteps();
        for (Element workflow : allWorkflowInstances) {
            List<ProcessStep> runningSteps = workflowUtilities.getRunningProcessSteps(workflow.getName());
            for (ProcessStep runningStep : runningSteps) {
                if (runningStep.getContainer() != null && runningStep.getContainer().getContainerStatus().equals(ContainerStatus.DEPLOYED) && runningStep.getStartDate() != null) {
                    stringBuilder.append(runningStep).append("\n");
                }
            }

            for (ProcessStep processStep : nextSteps) {
                if (!processStep.getWorkflowName().equals(workflow.getName())) {
                    continue;
                }
            }
        }
    }
}
