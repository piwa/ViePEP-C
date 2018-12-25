package at.ac.tuwien.infosys.viepepc.scheduler.frincu;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.library.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.exceptions.NoVmFoundException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Created by philippwaibel on 02/01/2017.
 */
@Slf4j
public abstract class AbstractProvisioningImpl {

    @Autowired
    protected CacheWorkflowService cacheWorkflowService;
    @Autowired
    protected CacheContainerService cacheContainerService;
    @Autowired
    protected ContainerImageRegistryReader containerImageRegistryReader;
    @Autowired
    protected WorkflowUtilities workflowUtilities;
    @Autowired
    protected CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    protected InMemoryCacheImpl inMemoryCache;

    protected boolean checkIfEnoughResourcesLeftOnVM(VirtualMachine vm, Container container, OptimizationResult optimizationResult) {
        return checkIfEnoughResourcesLeftOnVM(vm, container.getContainerConfiguration(), optimizationResult);
    }

    protected boolean checkIfEnoughResourcesLeftOnVM(VirtualMachine vm, ContainerConfiguration containerConfiguration, OptimizationResult optimizationResult) {

        Set<ProcessStep> scheduledProcessSteps = new HashSet(optimizationResult.getProcessSteps());
        scheduledProcessSteps.addAll(inMemoryCache.getWaitingForExecutingProcessSteps());

        double scheduledCPUUsage = scheduledProcessSteps.stream().filter(ps -> ps.getContainer().getVirtualMachine().equals(vm)).mapToDouble(ps -> ps.getContainer().getContainerConfiguration().getCPUPoints()).sum();
        double scheduledRAMUsage = scheduledProcessSteps.stream().filter(ps -> ps.getContainer().getVirtualMachine().equals(vm)).mapToDouble(ps -> ps.getContainer().getContainerConfiguration().getRam()).sum();
        double alreadyUsedCPU = vm.getDeployedContainers().stream().mapToDouble(c -> c.getContainerConfiguration().getCPUPoints()).sum();
        double alreadyUsedRAM = vm.getDeployedContainers().stream().mapToDouble(c -> c.getContainerConfiguration().getRam()).sum();
        double remainingCPUOnVm = vm.getVmType().getCpuPoints() - alreadyUsedCPU - scheduledCPUUsage;
        double remainingRAMOnVm = vm.getVmType().getRamPoints() - alreadyUsedRAM - scheduledRAMUsage;

        return containerConfiguration.getCPUPoints() <= remainingCPUOnVm && containerConfiguration.getRam() <= remainingRAMOnVm;

    }

    protected void deployContainerAssignProcessStep(ProcessStep nextProcessStep, Container container, VirtualMachine vm, OptimizationResult optimizationResult) throws ContainerImageNotFoundException, ContainerConfigurationNotFoundException {
        container.setVirtualMachine(vm);
        nextProcessStep.setContainer(container);
        optimizationResult.addProcessStep(nextProcessStep);
    }

    protected void deployContainerAssignProcessStep(ProcessStep nextProcessStep, VirtualMachine vm, OptimizationResult optimizationResult) throws ContainerImageNotFoundException, ContainerConfigurationNotFoundException {
        Container container = getContainer(nextProcessStep);
        deployContainerAssignProcessStep(nextProcessStep, container, vm, optimizationResult);
    }

    protected VirtualMachine startNewVMDeployContainerAssignProcessStep(ProcessStep processStep, OptimizationResult optimizationResult) throws ContainerConfigurationNotFoundException, ContainerImageNotFoundException, NoVmFoundException {
        Container container = getContainer(processStep);
        VirtualMachine newVM = startNewVmDefaultOrForContainer(optimizationResult, container.getContainerConfiguration());
        deployContainerAssignProcessStep(processStep, container, newVM, optimizationResult);
        return newVM;
    }

    protected VirtualMachine startNewVMDeployContainerAssignProcessStep(ProcessStep processStep, Container container, OptimizationResult optimizationResult) throws ContainerConfigurationNotFoundException, ContainerImageNotFoundException, NoVmFoundException {
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
        nextWorkflowInstances.forEach(workflowElement -> returnList.addAll(getNextProcessStepsSorted(workflowElement)));
        return returnList;
    }

    protected List<ProcessStep> getNextProcessStepsSorted(WorkflowElement workflow) {
        List<ProcessStep> list = Collections.synchronizedList(workflowUtilities.getNextSteps(workflow.getName()));
        list.sort(Comparator.comparingLong(ProcessStep::getExecutionTime).reversed());
        return list;
    }

    protected List<VirtualMachine> getRunningVms() {
        return new ArrayList<>(cacheVirtualMachineService.getStartedAndScheduledForStartVMs());
    }

    protected VirtualMachine startNewDefaultVm(OptimizationResult result) throws NoVmFoundException {
        return startNewVm(result, cacheVirtualMachineService.getDefaultVmType(), null);
    }


    protected abstract VirtualMachine startNewVm(OptimizationResult result, VMType vmType, ContainerConfiguration containerConfiguration) throws NoVmFoundException;

    protected VirtualMachine startNewVmDefaultOrForContainer(OptimizationResult result, ContainerConfiguration containerConfiguration) throws NoVmFoundException {

        VMType defaultVMType = cacheVirtualMachineService.getDefaultVmType();

        if (containerConfiguration.getCPUPoints() <= defaultVMType.getCpuPoints() && containerConfiguration.getRam() <= defaultVMType.getRamPoints()) {
            return startNewVm(result, defaultVMType, containerConfiguration);
        }

        Set<VMType> vmTypes = cacheVirtualMachineService.getVMTypes();
        for (VMType vmType : vmTypes) {
            if (containerConfiguration.getCPUPoints() <= vmType.getCpuPoints() && containerConfiguration.getRam() <= vmType.getRamPoints()) {
                return startNewVm(result, vmType, containerConfiguration);
            }
        }

        return null;
    }

    protected Container getContainer(ProcessStep nextStep) throws ContainerImageNotFoundException, ContainerConfigurationNotFoundException {
        ContainerConfiguration containerConfiguration = null;
        for (ContainerConfiguration tempContainerConfig : cacheContainerService.getContainerConfigurations(nextStep.getServiceType())) {
            if (containerConfiguration == null) {
                containerConfiguration = tempContainerConfig;
            } else if (containerConfiguration.getCPUPoints() > tempContainerConfig.getCPUPoints() || containerConfiguration.getRam() > tempContainerConfig.getRam()) {
                containerConfiguration = tempContainerConfig;
            }
        }
        if (containerConfiguration == null) {
            throw new ContainerConfigurationNotFoundException();
        }

        ContainerImage containerImage = containerImageRegistryReader.findContainerImage(nextStep.getServiceType());

        Container container = new Container();
        container.setContainerConfiguration(containerConfiguration);
        container.setContainerImage(containerImage);

        return container;
    }

    protected List<ProcessStep> getRunningSteps(WorkflowElement workflow) {
        return Collections.synchronizedList(workflowUtilities.getRunningProcessSteps(workflow.getName()));
    }

    protected List<ProcessStep> getAllRunningSteps(List<WorkflowElement> workflows) {
        Set<ProcessStep> runningProcesses = new HashSet<>();

        workflows.forEach(workflowElement -> runningProcesses.addAll(workflowUtilities.getRunningProcessSteps(workflowElement.getName())));
        return Collections.synchronizedList(new ArrayList<>(runningProcesses));
    }

    protected List<WorkflowElement> getRunningWorkflowInstancesSorted() {
        List<WorkflowElement> list = Collections.synchronizedList(cacheWorkflowService.getRunningWorkflowInstances());
        list.sort(Comparator.comparing(Element::getDeadline));
        return list;
    }

    protected void removeAllBusyVms(List<VirtualMachine> availableVms, List<WorkflowElement> runningWorkflowInstances) {
        Set<VirtualMachine> alreadyUsedVMs = new HashSet<>();
        runningWorkflowInstances.forEach(workflow -> getRunningSteps(workflow).forEach(ps -> alreadyUsedVMs.add(ps.getContainer().getVirtualMachine())));
        inMemoryCache.getWaitingForExecutingProcessSteps().forEach(ps -> alreadyUsedVMs.add(ps.getContainer().getVirtualMachine()));

        Set<VirtualMachine> forOutput = new HashSet<>();
        StringBuilder builder = new StringBuilder();
        availableVms.stream().filter(vm -> vm.getDeployedContainers().size() > 0).forEach(vm -> forOutput.add(vm));
        alreadyUsedVMs.forEach(vm -> forOutput.add(vm));
        forOutput.forEach(vm -> builder.append(vm.getInstanceId()).append(", "));
        log.info("Busy VMs: " + builder);

        availableVms.removeIf(vm -> alreadyUsedVMs.contains(vm));
        availableVms.removeIf(vm -> vm.getDeployedContainers().size() > 0);

    }

    protected long getRemainingLeasingDurationIncludingScheduled(DateTime tau_t, VirtualMachine vm, OptimizationResult optimizationResult) {
        List<ProcessStep> processSteps = new ArrayList<>(optimizationResult.getProcessSteps());
        return getRemainingLeasingDurationIncludingScheduled(tau_t, vm, processSteps);
    }

    protected long getRemainingLeasingDurationIncludingScheduled(DateTime tau_t, VirtualMachine vm, List<ProcessStep> processSteps) {
        DateTime startedAt = vm.getStartedAt();
        if (startedAt == null) {
            startedAt = tau_t;
        }
        DateTime toBeTerminatedAt = vm.getToBeTerminatedAt();
        if (toBeTerminatedAt == null) {
            toBeTerminatedAt = new DateTime(startedAt.getMillis() + vm.getVmType().getLeasingDuration());
        }
        long remainingLeasingDuration = toBeTerminatedAt.getMillis() - tau_t.getMillis();


        processSteps.addAll(inMemoryCache.getWaitingForExecutingProcessSteps());
        for (ProcessStep processStep : processSteps) {
            if (processStep.getScheduledAtVM() == vm || (processStep.getContainer() != null && processStep.getContainer().getVirtualMachine() == vm)) {
                remainingLeasingDuration = remainingLeasingDuration - processStep.getContainer().getContainerImage().getDeployTime() - processStep.getExecutionTime();
            }
        }

        if (remainingLeasingDuration < 0) {
            remainingLeasingDuration = 0;
        }
        return remainingLeasingDuration;

    }

    protected long getRemainingLeasingDuration(DateTime tau_t, VirtualMachine vm) {
        DateTime startedAt = vm.getStartedAt();
        if (startedAt == null) {
            startedAt = tau_t;
        }
        DateTime toBeTerminatedAt = vm.getToBeTerminatedAt();
        if (toBeTerminatedAt == null) {
            toBeTerminatedAt = new DateTime(startedAt.getMillis() + vm.getVmType().getLeasingDuration());
        }
        long remainingLeasingDuration = toBeTerminatedAt.getMillis() - tau_t.getMillis();

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
        DateTime now = DateTime.now();
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
