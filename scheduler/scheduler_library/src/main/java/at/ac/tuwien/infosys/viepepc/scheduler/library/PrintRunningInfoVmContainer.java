package at.ac.tuwien.infosys.viepepc.scheduler.library;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ActionExecutor;
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
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStepStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@Profile({"VmAndContainer", "Frincu"})
public class PrintRunningInfoVmContainer implements PrintRunningInfo {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheContainerService cacheContainerService;
    @Autowired
    private CacheProcessStepService cacheProcessStepService;
    @Autowired
    private ActionExecutor actionExecutor;

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
        stringBuilder.append("\nRunning Threads: ").append(threadSet.size()).append("\n");


        stringBuilder.append("\n------------------------------ VMs running -------------------------------\n");
        stringBuilder.append(cacheVirtualMachineService.getDeployedVMInstances().stream().map(vm -> vm.toString() + "\n").collect(Collectors.joining()));

        stringBuilder.append("--------------------------- Containers running ---------------------------\n");
        stringBuilder.append(cacheContainerService.getDeployedContainers().stream().map(container -> container + "\n").collect(Collectors.joining()));

        stringBuilder.append("----------------------------- Tasks running ------------------------------\n");
        stringBuilder.append(cacheProcessStepService.getRunningProcessSteps().stream().map(runningStep -> runningStep + "\n").collect(Collectors.joining()));

    }


    private void printWaitingInformation(StringBuilder stringBuilder) {

        List<VirtualMachineInstance> tempVms = cacheVirtualMachineService.getDeployedVMInstances();

        stringBuilder.append("------------------------ VMs waiting for starting ------------------------\n");
        List<VirtualMachineInstance> vms = cacheVirtualMachineService.getScheduledVMInstances();
        tempVms.addAll(vms);
        vms.addAll(cacheVirtualMachineService.getDeployingVMInstances());
        for (VirtualMachineInstance vm : vms) {
            stringBuilder.append(vm.toString()).append("\n");
        }
        stringBuilder.append("-------------------- Containers waiting for starting ---------------------\n");
        Set<Container> containers = new HashSet<>(cacheContainerService.getAllContainerInstances().values());
        for (Container container : containers) {
            if(!tempVms.contains(container.getVirtualMachineInstance())){
                if(container.getScheduledCloudResourceUsage().getEnd().isBeforeNow()) {
                    container.setContainerStatus(ContainerStatus.TERMINATED);
                }
            }
            if (container.getContainerStatus().equals(ContainerStatus.SCHEDULED) || container.getContainerStatus().equals(ContainerStatus.DEPLOYING)) {
                stringBuilder.append(container.toString()).append("\n");
            }
        }
        stringBuilder.append("----------------------- Tasks waiting for starting -----------------------\n");
        List<ProcessStep> processSteps = cacheProcessStepService.getScheduledProcessSteps();
        processSteps.addAll(cacheProcessStepService.getDeployingProcessSteps());
        for (ProcessStep processStep : processSteps) {
            if(!containers.contains(processStep.getContainer()) || (processStep.getContainer().getVirtualMachineInstance()!= null && !vms.contains(processStep.getContainer().getVirtualMachineInstance()))){
                if(processStep.getScheduledStartDate().plus(processStep.getExecutionTime()).isBeforeNow()) {
                    processStep.setProcessStepStatus(ProcessStepStatus.DONE);
                    processStep.setStartDate(processStep.getScheduledStartDate());
                    processStep.setFinishedAt(processStep.getScheduledStartDate().plus(processStep.getExecutionTime()));
                    if (processStep.isLastElement()) {
                        actionExecutor.checkIfWorkflowDone(processStep);
                    }
                }
            }
            stringBuilder.append(processStep.toString()).append("\n");
        }
    }

}
