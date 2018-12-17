package at.ac.tuwien.infosys.viepepc.scheduler.library;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@Profile({"VmAndContainer", "Frincu"})
public class PrintRunningInfoVmContainer implements PrintRunningInfo {

    @Autowired
    private WorkflowUtilities workflowUtilities;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    @Override
    public void printRunningInformation() {
        StringBuilder stringBuilder = new StringBuilder();

        printRunningInformation(stringBuilder);
        stringBuilder.append("\n");

        printWaitingInformation(stringBuilder);
        stringBuilder.append("\n");

        log.info(stringBuilder.toString());
    }

    private void printRunningInformation(StringBuilder stringBuilder) {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        stringBuilder.append("\nRunning Threads: " + threadSet.size() + "\n");


        stringBuilder.append("\n------------------------------ VMs running -------------------------------\n");
        List<VirtualMachine> vMs = cacheVirtualMachineService.getAllVMs();
        for (VirtualMachine vm : vMs) {
            if (vm.isLeased() && vm.isStarted()) {
                stringBuilder.append(vm.toString()).append("\n");
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


    private void printWaitingInformation(StringBuilder stringBuilder) {
        stringBuilder.append("------------------------ VMs waiting for starting ------------------------\n");
        Set<VirtualMachine> vms = inMemoryCache.getWaitingForExecutingProcessSteps().stream().map(ProcessStep::getScheduledAtVM).collect(Collectors.toSet());
        for (VirtualMachine vm : vms) {
            if (!vm.isStarted()) {
                stringBuilder.append(vm.toString()).append("\n");
            }
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

    private void getRunningTasks(StringBuilder stringBuilder) {
        List<WorkflowElement> allWorkflowInstances = cacheWorkflowService.getRunningWorkflowInstances();
        List<ProcessStep> nextSteps = workflowUtilities.getNotStartedUnfinishedSteps();
        for (Element workflow : allWorkflowInstances) {
            List<ProcessStep> runningSteps = workflowUtilities.getRunningProcessSteps(workflow.getName());
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
