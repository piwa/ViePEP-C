package at.ac.tuwien.infosys.viepepc.reasoner.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.ProcessOptimizationResults;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.ServiceExecutionController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.AsyncResult;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Scope("prototype")
@Slf4j
public class ProcessOptimizationResultsImpl implements ProcessOptimizationResults {


    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private ServiceExecutionController serviceExecutionController;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;


    @Override
    public Future<Boolean> processResults(OptimizationResult optimize, Date tau_t) {

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("------------------------- VMs running ----------------------------\n");
        List<VirtualMachine> vMs = cacheVirtualMachineService.getAllVMs();
        for(VirtualMachine vm : vMs) {
            if(vm.isLeased() && vm.isStarted()) {
                stringBuilder.append(vm.toString()).append("\n");
            }
        }

        stringBuilder.append("------------------------- Containers running ----------------------------\n");
        for(VirtualMachine vm : vMs) {
            for(Container container : vm.getDeployedContainers()) {
                if(container.isRunning()) {
                    stringBuilder.append(container.toString()).append("\n");
                }
            }
        }

        stringBuilder.append("------------------------ Tasks running ---------------------------\n");
        getRunningTasks(stringBuilder);

        Set<VirtualMachine> vmsToStart = new HashSet<>();
        Set<Container> containersToDeploy = new HashSet<>();
        processProcessSteps(optimize, vmsToStart, containersToDeploy, tau_t);
        stringBuilder.append("----------- VM should be used (running or has to be started): ----\n");
        for(VirtualMachine virtualMachine : vmsToStart) {
            stringBuilder.append(virtualMachine).append("\n");
        }

        stringBuilder.append("----------- Container should be used (running or has to be started): ----\n");
        for (Container container : containersToDeploy) {
            stringBuilder.append(container).append("\n");
        }

        stringBuilder.append("----------------------- Tasks to be started ----------------------\n");
        for (ProcessStep processStep : optimize.getProcessSteps()) {
            stringBuilder.append(processStep).append("\n");
        }

        log.info(stringBuilder.toString());

        serviceExecutionController.startInvocationViaContainers(optimize.getProcessSteps());

        cleanupVMs(tau_t);

        return new AsyncResult<Boolean>(true);
    }

    private void processProcessSteps(OptimizationResult optimize, Set<VirtualMachine> vmsToStart, Set<Container> containersToDeploy, Date tau_t) {
        for(ProcessStep processStep : optimize.getProcessSteps()) {
            if(processStep.getScheduledAtVM() != null) {
                vmsToStart.add(processStep.getScheduledAtVM());
            }
            if(processStep.getScheduledAtContainer().getVirtualMachine() != null) {
                vmsToStart.add(processStep.getScheduledAtContainer().getVirtualMachine());
            }
            containersToDeploy.add(processStep.getScheduledAtContainer());
            if(processStep.getScheduledAtContainer() != null) {
                processStep.setScheduledForExecution(true, tau_t, processStep.getScheduledAtContainer());
            }
            else if(processStep.getScheduledAtVM() != null) {
                processStep.setScheduledForExecution(true, tau_t, processStep.getScheduledAtVM());
            }
            else {
                processStep.setScheduledForExecution(false, new Date(0), (Container)null);
            }
        }
    }


    private void getRunningTasks(StringBuilder stringBuilder) {
        List<WorkflowElement> allWorkflowInstances = cacheWorkflowService.getRunningWorkflowInstances();
        List<ProcessStep> nextSteps = placementHelper.getNotStartedUnfinishedSteps();
        for (Element workflow : allWorkflowInstances) {
            List<ProcessStep> runningSteps = placementHelper.getRunningProcessSteps(workflow.getName());
            for (ProcessStep runningStep : runningSteps) {
                if(runningStep.getScheduledAtVM() != null && runningStep.getScheduledAtVM().isStarted() ||
                   runningStep.getScheduledAtContainer() != null && runningStep.getScheduledAtContainer().isRunning()) {
                    stringBuilder.append("Task-Running: ").append(runningStep).append("\n");
                }
            }

            for (ProcessStep processStep : nextSteps) {
                if (!processStep.getWorkflowName().equals(workflow.getName())) {
                    continue;
                }
            }
        }
    }

    private void cleanupVMs(Date tau_t_0) {
        List<VirtualMachine> vMs = cacheVirtualMachineService.getAllVMs();
        for (VirtualMachine vM : vMs) {
            if (vM.getToBeTerminatedAt() != null && vM.getToBeTerminatedAt().before((tau_t_0))) {
                placementHelper.terminateVM(vM);
            }
        }
    }
}
