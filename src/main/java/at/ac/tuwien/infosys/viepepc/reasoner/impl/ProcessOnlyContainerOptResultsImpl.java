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
@Profile({"OnlyContainerGeneticAlgorithm", "OnlyContainerBaseline"})
public class ProcessOnlyContainerOptResultsImpl implements ProcessOptimizationResults {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private ServiceExecutionController serviceExecutionController;

    private Set<Container> waitingForExecutingContainers = new HashSet<>();
    private boolean printRunningInformation = false;

    @Override
    public Future<Boolean> processResults(OptimizationResult optimize, DateTime tau_t) {

        inMemoryCache.getWaitingForExecutingProcessSteps().addAll(optimize.getProcessSteps());
        optimize.getProcessSteps().stream().filter(ps -> ps.getScheduledAtContainer() != null).forEach(ps -> waitingForExecutingContainers.add(ps.getScheduledAtContainer()));

        serviceExecutionController.startInvocationViaContainers(optimize.getProcessSteps());

        if(printRunningInformation) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Optimization result:\n");
            printOptimizationResultInformation(optimize, tau_t, stringBuilder);
            log.debug(stringBuilder.toString());
        }

        return new AsyncResult<Boolean>(true);
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

}
