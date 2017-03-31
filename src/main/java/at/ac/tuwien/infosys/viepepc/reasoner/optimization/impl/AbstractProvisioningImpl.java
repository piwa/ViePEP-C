package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.ContainerImageNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 02/01/2017.
 */
public abstract class AbstractProvisioningImpl {

    @Autowired
    protected CacheWorkflowService cacheWorkflowService;
    @Autowired
    protected CacheContainerService cacheContainerService;
    @Autowired
    protected ContainerImageRegistryReader containerImageRegistryReader;
    @Autowired
    protected PlacementHelper placementHelper;
    @Autowired
    protected CacheVirtualMachineService cacheVirtualMachineService;

    protected boolean checkIfEnoughResourcesLeftOnVM(VirtualMachine vm, Container container, OptimizationResult optimizationResult) {
//        double scheduledCPUUsage = optimizationResult.getProcessSteps().stream().mapToDouble(ps -> ps.getServiceType().getServiceTypeResources().getCpuLoad()).sum();
//        double scheduledRAMUsage = optimizationResult.getProcessSteps().stream().mapToDouble(ps -> ps.getServiceType().getServiceTypeResources().getMemory()).sum();
        double scheduledCPUUsage = optimizationResult.getProcessSteps().stream().mapToDouble(ps -> ps.getScheduledAtContainer().getContainerConfiguration().getCPUPoints()).sum();
        double scheduledRAMUsage = optimizationResult.getProcessSteps().stream().mapToDouble(ps -> ps.getScheduledAtContainer().getContainerConfiguration().getRam()).sum();
        double alreadyUsedCPU = vm.getDeployedContainers().stream().mapToDouble(c -> c.getContainerConfiguration().getCPUPoints()).sum();
        double alreadyUsedRAM = vm.getDeployedContainers().stream().mapToDouble(c -> c.getContainerConfiguration().getRam()).sum();
        double remainingCPUOnVm = vm.getVmType().getCpuPoints() - alreadyUsedCPU - scheduledCPUUsage;
        double remainingRAMOnVm = vm.getVmType().getRamPoints() - alreadyUsedRAM - scheduledRAMUsage;

        return container.getContainerConfiguration().getCPUPoints() <= remainingCPUOnVm && container.getContainerConfiguration().getRam() <= remainingRAMOnVm;

    }

    protected void deployContainerAssignProcessStep(ProcessStep nextProcessStep, Container container, VirtualMachine vm, OptimizationResult optimizationResult) throws ContainerImageNotFoundException, ContainerConfigurationNotFoundException {
        container.setVirtualMachine(vm);
        nextProcessStep.setScheduledAtContainer(container);
        optimizationResult.addProcessStep(nextProcessStep);
    }

    protected void deployContainerAssignProcessStep(ProcessStep nextProcessStep, VirtualMachine vm, OptimizationResult optimizationResult) throws ContainerImageNotFoundException, ContainerConfigurationNotFoundException {
        Container container = getContainer(nextProcessStep);
        deployContainerAssignProcessStep(nextProcessStep, container, vm, optimizationResult);
    }

    protected VirtualMachine startNewVMDeployContainerAssignProcessStep(ProcessStep processStep, OptimizationResult optimizationResult) throws ContainerConfigurationNotFoundException, ContainerImageNotFoundException {
        Container container = getContainer(processStep);
        VirtualMachine newVM = startNewVmDefaultOrForContainer(optimizationResult, container.getContainerConfiguration());
        deployContainerAssignProcessStep(processStep, container, newVM, optimizationResult);
        return newVM;
    }

    protected VirtualMachine startNewVMDeployContainerAssignProcessStep(ProcessStep processStep, Container container, OptimizationResult optimizationResult) throws ContainerConfigurationNotFoundException, ContainerImageNotFoundException {
        VirtualMachine newVM = startNewVmDefaultOrForContainer(optimizationResult, container.getContainerConfiguration());
        deployContainerAssignProcessStep(processStep, container, newVM, optimizationResult);
        return newVM;
    }

    protected ProcessStep getMostUrgentProcessStep(List<WorkflowElement> nextWorkflowInstances) {
        for (WorkflowElement workflowElement : nextWorkflowInstances) {
            List<ProcessStep> nextSteps = getNextProcessStepsSorted(workflowElement);
            if (nextSteps.size() > 0) {
                return nextSteps.get(0);
            }
        }

        return null;
    }

    protected List<ProcessStep> getNextProcessStepsSorted(List<WorkflowElement> nextWorkflowInstances) {
        List<ProcessStep> returnList = new ArrayList<>();
        for (WorkflowElement workflowElement : nextWorkflowInstances) {
            returnList.addAll(getNextProcessStepsSorted(workflowElement));
        }
        return returnList;
    }

    protected List<ProcessStep> getNextProcessStepsSorted(WorkflowElement workflow) {
        List<ProcessStep> list = Collections.synchronizedList(placementHelper.getNextSteps(workflow.getName()));
        list.sort(Comparator.comparingLong(ProcessStep::getExecutionTime).reversed());
        return list;
    }

    protected List<VirtualMachine> getRunningVms() {
        return new ArrayList<>(cacheVirtualMachineService.getStartedAndScheduledForStartVMs());
    }

    protected VirtualMachine startNewDefaultVm(OptimizationResult result) {
        return startNewVm(result, cacheVirtualMachineService.getDefaultVmType());
    }

    protected VirtualMachine startNewVm(OptimizationResult result, VMType vmType) {
        List<VirtualMachine> virtualMachineList = cacheVirtualMachineService.getVMs(vmType);
        List<VirtualMachine> vmsShouldBeStarted = result.getProcessSteps().stream().map(ProcessStep::getScheduledAtVM).collect(Collectors.toList());
        vmsShouldBeStarted.addAll(result.getProcessSteps().stream().map(processStep -> processStep.getScheduledAtContainer().getVirtualMachine()).collect(Collectors.toList()));

        for (VirtualMachine currentVM : virtualMachineList) {
            if (!vmsShouldBeStarted.contains(currentVM) && !currentVM.isLeased() && !currentVM.isStarted()) {
                return currentVM;
            }
        }
        return null;
    }

    protected VirtualMachine startNewVmDefaultOrForContainer(OptimizationResult result, ContainerConfiguration containerConfiguration) {

        VMType defaultVMType = cacheVirtualMachineService.getDefaultVmType();

        if(containerConfiguration.getCPUPoints() <= defaultVMType.getCpuPoints() && containerConfiguration.getRam() <= defaultVMType.getRamPoints()) {
            return startNewVm(result, defaultVMType);
        }

        Set<VMType> vmTypes = cacheVirtualMachineService.getVMTypes();
        for(VMType vmType : vmTypes) {
            if(containerConfiguration.getCPUPoints() <= vmType.getCpuPoints() && containerConfiguration.getRam() <= vmType.getRamPoints()) {
                return startNewVm(result, vmType);
            }
        }

        return null;
    }

    protected Container getContainer(ProcessStep nextStep) throws ContainerImageNotFoundException, ContainerConfigurationNotFoundException {
        ContainerConfiguration containerConfiguration = null;
        for (ContainerConfiguration tempContainerConfig : cacheContainerService.getContainerConfigurations(nextStep.getServiceType())) {
            if (containerConfiguration == null) {
                containerConfiguration = tempContainerConfig;
            }
            else if (containerConfiguration.getCPUPoints() > tempContainerConfig.getCPUPoints() || containerConfiguration.getRam() > tempContainerConfig.getRam()) {
                containerConfiguration = tempContainerConfig;
            }
        }
        if(containerConfiguration == null) {
            throw new ContainerConfigurationNotFoundException();
        }

        ContainerImage containerImage = containerImageRegistryReader.findContainerImage(nextStep.getServiceType());

        Container container = new Container();
        container.setContainerConfiguration(containerConfiguration);
        container.setContainerImage(containerImage);

        return container;
    }

    protected List<ProcessStep> getRunningSteps(WorkflowElement workflow) {
        return Collections.synchronizedList(placementHelper.getRunningProcessSteps(workflow.getName()));
    }

    protected List<ProcessStep> getAllRunningSteps(List<WorkflowElement> workflows) {
        Set<ProcessStep> runningProcesses = new HashSet<>();
        for (WorkflowElement workflowElement : workflows) {
            runningProcesses.addAll(placementHelper.getRunningProcessSteps(workflowElement.getName()));
        }
        return Collections.synchronizedList(new ArrayList<>(runningProcesses));
    }

    protected List<WorkflowElement> getRunningWorkflowInstancesSorted() {
        List<WorkflowElement> list = Collections.synchronizedList(cacheWorkflowService.getRunningWorkflowInstances());
        list.sort(Comparator.comparing(Element::getDeadline));
        return list;
    }

    protected void removeAllBusyVms(List<VirtualMachine> availableVms) {
        availableVms.removeIf(vm -> vm.getDeployedContainers().size() > 0);
    }

    protected long getRemainingLeasingDurationIncludingScheduled(Date tau_t, VirtualMachine vm, OptimizationResult optimizationResult) {
        Date startedAt = vm.getStartedAt();
        if (startedAt == null) {
            startedAt = tau_t;
        }
        Date toBeTerminatedAt = vm.getToBeTerminatedAt();
        if (toBeTerminatedAt == null) {
            toBeTerminatedAt = new Date(startedAt.getTime() + vm.getVmType().getLeasingDuration());
        }
        long remainingLeasingDuration = toBeTerminatedAt.getTime() - tau_t.getTime();

        for(ProcessStep processStep : optimizationResult.getProcessSteps()) {
            if(processStep.getScheduledAtVM() == vm || processStep.getScheduledAtContainer().getVirtualMachine() == vm) {
                remainingLeasingDuration = remainingLeasingDuration - processStep.getExecutionTime();
            }
        }

        if (remainingLeasingDuration < 0) {
            remainingLeasingDuration = 0;
        }
        return remainingLeasingDuration;

    }

    protected long getRemainingLeasingDuration(Date tau_t, VirtualMachine vm) {
        Date startedAt = vm.getStartedAt();
        if (startedAt == null) {
            startedAt = tau_t;
        }
        Date toBeTerminatedAt = vm.getToBeTerminatedAt();
        if (toBeTerminatedAt == null) {
            toBeTerminatedAt = new Date(startedAt.getTime() + vm.getVmType().getLeasingDuration());
        }
        long remainingLeasingDuration = toBeTerminatedAt.getTime() - tau_t.getTime();

        if (remainingLeasingDuration < 0) {
            remainingLeasingDuration = 0;
        }
        return remainingLeasingDuration;

    }

    protected boolean vmAlreadyUsedInResult(VirtualMachine vm, OptimizationResult optimizationResult) {
        return optimizationResult.getProcessSteps().stream().anyMatch(ps -> (ps.getScheduledAtContainer() != null && ps.getScheduledAtContainer().getVirtualMachine() == vm) || ps.getScheduledAtVM() == vm);
    }

    protected long calcRemainingRunningProcessStepExecution(List<ProcessStep> runningProcessSteps) {
        long remainingRunningProcessStepExecution = -1;
        Date now = new Date();
        for (ProcessStep processStep : runningProcessSteps) {
            if (remainingRunningProcessStepExecution == -1 && remainingRunningProcessStepExecution < processStep.getRemainingExecutionTime(now)) {
                remainingRunningProcessStepExecution = processStep.getRemainingExecutionTime(now);
            }
        }
        return remainingRunningProcessStepExecution;
    }

    protected void calcTauT1(OptimizationResult optimizationResult, long executionDurationFirstProcessStep, ProcessStep processStep) {
        long difference = executionDurationFirstProcessStep - processStep.getExecutionTime();
        if (optimizationResult.getTauT1() == -1 || optimizationResult.getTauT1() > difference) {
            optimizationResult.setTauT1((new Date()).getTime() + difference);
        }
    }

}
