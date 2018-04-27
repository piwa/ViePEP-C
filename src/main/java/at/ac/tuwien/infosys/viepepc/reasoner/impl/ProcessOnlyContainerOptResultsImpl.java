package at.ac.tuwien.infosys.viepepc.reasoner.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.ProcessOptimizationResults;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.ServiceExecutionController;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Scope("prototype")
@Component
@Slf4j
@Profile("OnlyContainerGeneticAlgorithm")
public class ProcessOnlyContainerOptResultsImpl implements ProcessOptimizationResults {


    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private ServiceExecutionController serviceExecutionController;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    private Set<Container> waitingForExecutingContainers = new HashSet<>();

    @Override
    public Future<Boolean> processResults(OptimizationResult optimize, DateTime tau_t) {

        inMemoryCache.getWaitingForExecutingProcessSteps().addAll(optimize.getProcessSteps());
        optimize.getProcessSteps().stream().filter(ps -> ps.getScheduledAtContainer() != null).forEach(ps -> waitingForExecutingContainers.add(ps.getScheduledAtContainer()));

        serviceExecutionController.startInvocationViaContainers(optimize.getProcessSteps());


        StringBuilder stringBuilder = new StringBuilder();

        printRunningInformation(stringBuilder);
        stringBuilder.append("\n");

        printWaitingInformation(stringBuilder);
        stringBuilder.append("\n");

        printOptimizationResultInformation(optimize, tau_t, stringBuilder);

        log.info(stringBuilder.toString());

        return new AsyncResult<Boolean>(true);
    }

    public void printRunningInformation(StringBuilder stringBuilder) {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        stringBuilder.append("\nRunning Threads: " + threadSet.size() + "\n");

        stringBuilder.append("--------------------------- Containers running ---------------------------\n");
        for (Container container : inMemoryCache.getRunningContainers()) {
            if (container.isRunning()) {
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


    private void printOptimizationResultInformation(OptimizationResult optimize, DateTime tau_t, StringBuilder stringBuilder) {
        Set<Container> containersToDeploy = new HashSet<>();
        for (ProcessStep processStep : optimize.getProcessSteps()) {
            containersToDeploy.add(processStep.getScheduledAtContainer());
        }

        stringBuilder.append("-------- Container should be used (running or has to be started): --------\n");
        for (Container container : containersToDeploy) {
            stringBuilder.append(container).append("\n");
        }

        stringBuilder.append("-------------------------- Tasks to be started ---------------------------\n");
        for (ProcessStep processStep : optimize.getProcessSteps()) {
            stringBuilder.append(processStep).append("\n");
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
