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
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Scope("prototype")
@Component
@Slf4j
@Conditional(NotOnlyContainerCondition.class)
public class ProcessOptimizationResultsImpl implements ProcessOptimizationResults {


    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private ServiceExecutionController serviceExecutionController;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    private Set<Container> waitingForExecutingContainers = new HashSet<>();
    private Set<VirtualMachine> waitingForExecutingVirtualMachines = new HashSet<>();



    @Override
    public Future<Boolean> processResults(OptimizationResult optimize, DateTime tau_t) {

        inMemoryCache.getWaitingForExecutingProcessSteps().addAll(optimize.getProcessSteps());
        optimize.getProcessSteps().stream().filter(ps -> ps.getScheduledAtVM() != null).forEach(ps -> waitingForExecutingVirtualMachines.add(ps.getScheduledAtVM()));
        optimize.getProcessSteps().stream().filter(ps -> ps.getScheduledAtContainer().getVirtualMachine() != null).forEach(ps -> waitingForExecutingVirtualMachines.add(ps.getScheduledAtContainer().getVirtualMachine()));

        serviceExecutionController.startInvocationViaContainersOnVms(optimize.getProcessSteps());


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


        stringBuilder.append("\n------------------------------ VMs running -------------------------------\n");
        List<VirtualMachine> vMs = cacheVirtualMachineService.getAllVMs();
        for (VirtualMachine vm : vMs) {
            if (vm.isLeased() && vm.isStarted()) {
                stringBuilder.append(vm.toString()).append("\n");
                waitingForExecutingVirtualMachines.remove(vm);
            }
        }

        stringBuilder.append("--------------------------- Containers running ---------------------------\n");
        for (VirtualMachine vm : vMs) {
            for (Container container : vm.getDeployedContainers()) {
                if (container.isRunning()) {
                    stringBuilder.append(container.toString()).append("\n");
                }
            }
        }

        stringBuilder.append("----------------------------- Tasks running ------------------------------\n");
        getRunningTasks(stringBuilder);
    }


    public void printWaitingInformation(StringBuilder stringBuilder) {
        stringBuilder.append("------------------------ VMs waiting for starting ------------------------\n");
        for (VirtualMachine vm : waitingForExecutingVirtualMachines) {
            stringBuilder.append(vm.toString()).append("\n");
        }
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
        Set<VirtualMachine> vmsToStart = new HashSet<>();
        Set<Container> containersToDeploy = new HashSet<>();
        processProcessSteps(optimize, vmsToStart, containersToDeploy, tau_t);
        stringBuilder.append("----------- VM should be used (running or has to be started): ------------\n");
        for (VirtualMachine virtualMachine : vmsToStart) {
            stringBuilder.append(virtualMachine).append("\n");
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


    private void processProcessSteps(OptimizationResult optimize, Set<VirtualMachine> vmsToStart, Set<Container> containersToDeploy, DateTime tau_t) {
        for (ProcessStep processStep : optimize.getProcessSteps()) {
            if (processStep.getScheduledAtVM() != null) {
                vmsToStart.add(processStep.getScheduledAtVM());
            }
            if (processStep.getScheduledAtContainer().getVirtualMachine() != null) {
                vmsToStart.add(processStep.getScheduledAtContainer().getVirtualMachine());
            }
            containersToDeploy.add(processStep.getScheduledAtContainer());
            if (processStep.getScheduledAtContainer() != null) {
                processStep.setScheduledForExecution(true, tau_t, processStep.getScheduledAtContainer());
            } else if (processStep.getScheduledAtVM() != null) {
                processStep.setScheduledForExecution(true, tau_t, processStep.getScheduledAtVM());
            } else {
                processStep.setScheduledForExecution(false, new DateTime(0), (Container) null);
            }
        }
    }


    private void getRunningTasks(StringBuilder stringBuilder) {
        List<WorkflowElement> allWorkflowInstances = cacheWorkflowService.getRunningWorkflowInstances();
        List<ProcessStep> nextSteps = placementHelper.getNotStartedUnfinishedSteps();
        for (Element workflow : allWorkflowInstances) {
            List<ProcessStep> runningSteps = placementHelper.getRunningProcessSteps(workflow.getName());
            for (ProcessStep runningStep : runningSteps) {
                if (((runningStep.getScheduledAtVM() != null && runningStep.getScheduledAtVM().isStarted()) || (runningStep.getScheduledAtContainer() != null && runningStep.getScheduledAtContainer().isRunning())) &&
                        runningStep.getStartDate() != null) {
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
