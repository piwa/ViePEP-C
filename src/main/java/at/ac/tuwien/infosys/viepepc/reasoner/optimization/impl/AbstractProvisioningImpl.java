package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
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

    protected void deployContainerAssignProcessStep(ProcessStep nextProcessStep, VirtualMachine vm, OptimizationResult optimizationResult) throws ContainerImageNotFoundException, ContainerConfigurationNotFoundException {
        Container container = getContainer(nextProcessStep);
        container.setVirtualMachine(vm);
        nextProcessStep.setScheduledAtContainer(container);
        optimizationResult.addProcessStep(nextProcessStep);
    }

    protected void startNewVMDeployContainerAssignProcessStep(ProcessStep processStep, OptimizationResult optimizationResult) throws ContainerConfigurationNotFoundException, ContainerImageNotFoundException {
        VirtualMachine newVM = startNewVm(optimizationResult);
        deployContainerAssignProcessStep(processStep, newVM, optimizationResult);
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
        list.sort((ps1, ps2) -> new Long(ps1.getDeadline()).compareTo(new Long(ps2.getDeadline())));
        return list;
    }

    protected List<VirtualMachine> getRunningVms() {
        return new ArrayList<>(cacheVirtualMachineService.getStartedAndScheduledForStartVMs());
    }

    protected VirtualMachine startNewVm(OptimizationResult result) {
        List<VirtualMachine> virtualMachineList = cacheVirtualMachineService.getVMs(cacheVirtualMachineService.getDefaultVmType());
        List<VirtualMachine> vmsShouldBeStarted = result.getProcessSteps().stream().map(ProcessStep::getScheduledAtVM).collect(Collectors.toList());
        vmsShouldBeStarted.addAll(result.getProcessSteps().stream().map(processStep -> processStep.getScheduledAtContainer().getVirtualMachine()).collect(Collectors.toList()));

        for (VirtualMachine currentVM : virtualMachineList) {
            if (!vmsShouldBeStarted.contains(currentVM) && !currentVM.isLeased() && !currentVM.isStarted()) {
                return currentVM;
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

    protected List<WorkflowElement> getNextWorkflowInstancesSorted() {
        List<WorkflowElement> list = Collections.synchronizedList(cacheWorkflowService.getRunningWorkflowInstances());
        list.sort((w1, w2) -> new Long(w1.getDeadline()).compareTo(new Long(w2.getDeadline())));
        return list;
    }

}
