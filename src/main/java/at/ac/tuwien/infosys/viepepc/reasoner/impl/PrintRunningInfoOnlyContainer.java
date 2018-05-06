package at.ac.tuwien.infosys.viepepc.reasoner.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PrintRunningInfoOnlyContainer {

    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;


    public void printRunningInformation() {
        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Running Workflows:\n");
            printRunningInformation(stringBuilder);
            stringBuilder.append("\n");

            printWaitingInformation(stringBuilder);

            log.info(stringBuilder.toString());
        } catch (Exception ex) {
            log.error("Exception while printing running information. But is ignored :D");
        }
    }


    public void printRunningInformation(StringBuilder stringBuilder) {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        stringBuilder.append("Running Threads: " + threadSet.size() + "\n");

        stringBuilder.append("--------------------------- Containers running ---------------------------\n");
        for (Container container : inMemoryCache.getRunningContainers()) {
            if (container!= null && container.isRunning()) {
                stringBuilder.append(container.toString()).append("\n");
            }
        }

        stringBuilder.append("----------------------------- Tasks running ------------------------------\n");
        getRunningTasks(stringBuilder);
    }


    public void printWaitingInformation(StringBuilder stringBuilder) {
        stringBuilder.append("-------------------- Containers waiting for starting ---------------------\n");
        Set<Container> containers = inMemoryCache.getWaitingForExecutingProcessSteps().stream().map(ProcessStep::getScheduledAtContainer).collect(Collectors.toSet());
        for (Container container : containers) {
            if (!container.isRunning()) {
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
        List<ProcessStep> nextSteps = placementHelper.getNotStartedUnfinishedSteps();
        for (Element workflow : allWorkflowInstances) {
            List<ProcessStep> runningSteps = placementHelper.getRunningProcessSteps(workflow.getName());
            for (ProcessStep runningStep : runningSteps) {
                if (runningStep.getScheduledAtContainer() != null && runningStep.getScheduledAtContainer().isRunning() && runningStep.getStartDate() != null) {
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
