package at.ac.tuwien.infosys.viepepc.scheduler.library;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheProcessStepService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
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
    private CacheContainerService cacheContainerService;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private CacheProcessStepService cacheProcessStepService;

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
        List<VirtualMachineInstance> deployedVMInstances = cacheVirtualMachineService.getDeployedVMInstances();
        for (VirtualMachineInstance vm : deployedVMInstances) {
            stringBuilder.append(vm.toString()).append("\n");
        }

        stringBuilder.append("--------------------------- Containers running ---------------------------\n");
        for (Container container : cacheContainerService.getDeployedContainers()) {
            stringBuilder.append(container.toString()).append("\n");

        }
        stringBuilder.append("----------------------------- Tasks running ------------------------------\n");
        getRunningTasks(stringBuilder);
    }


    private void printWaitingInformation(StringBuilder stringBuilder) {

        stringBuilder.append("------------------------ VMs waiting for starting ------------------------\n");
        List<VirtualMachineInstance> vms = cacheVirtualMachineService.getDeployedVMInstances();
        for (VirtualMachineInstance vm : vms) {
            if (vm.getVirtualMachineStatus().equals(VirtualMachineStatus.SCHEDULED) || vm.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYING)) {
                stringBuilder.append(vm.toString()).append("\n");
            }
        }
        stringBuilder.append("-------------------- Containers waiting for starting ---------------------\n");
        List<Container> containers = cacheContainerService.getAllContainerInstances();
        for (Container container : containers) {
            if (container.getContainerStatus().equals(ContainerStatus.SCHEDULED) || container.getContainerStatus().equals(ContainerStatus.DEPLOYING)) {
                stringBuilder.append(container.toString()).append("\n");
            }
        }
        stringBuilder.append("----------------------- Tasks waiting for starting -----------------------\n");
        // TODO
//        for (ProcessStep processStep : cacheProcessStepService.getProcessStepsWaitingForExecution()) {
//            stringBuilder.append(processStep.toString()).append("\n");
//        }
    }

    private void getRunningTasks(StringBuilder stringBuilder) {
        List<WorkflowElement> allWorkflowInstances = cacheWorkflowService.getRunningWorkflowInstances();
        List<ProcessStep> nextSteps = workflowUtilities.getNotStartedUnfinishedSteps();
        for (Element workflow : allWorkflowInstances) {
            List<ProcessStep> runningSteps = workflowUtilities.getRunningProcessSteps(workflow.getName());
            for (ProcessStep runningStep : runningSteps) {
                if ((runningStep.getContainer() != null && runningStep.getContainer().getContainerStatus().equals(ContainerStatus.DEPLOYED)) && runningStep.getStartDate() != null) {
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
