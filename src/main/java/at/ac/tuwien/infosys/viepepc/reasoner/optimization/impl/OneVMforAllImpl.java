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
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class OneVMforAllImpl implements ProcessInstancePlacementProblem {

    @Autowired
    protected PlacementHelper placementHelper;
    @Autowired
    protected CacheWorkflowService cacheWorkflowService;
    @Autowired
    protected CacheContainerService cacheContainerService;
    @Autowired
    protected CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    protected ContainerImageRegistryReader containerImageRegistryReader;

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(Date tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResultImpl();

        try {
            placementHelper.setFinishedWorkflows();

            List<WorkflowElement> nextWorkflowInstances = getNextWorkflowInstancesSorted();
            List<VirtualMachine> runningVMs = getRunningVms();
            List<ProcessStep> runningProcessSteps = getAllRunningSteps(nextWorkflowInstances);
            ProcessStep nextProcessStep = getMostUrgentProcessStep(nextWorkflowInstances);

            if (runningProcessSteps.size() > 0 || nextProcessStep == null) {
                return optimizationResult;
            }
            if (runningVMs.size() == 0) {                           // start new vm, deploy container and start first service
                VirtualMachine newVM = startNewVm(optimizationResult);
                Container container = getContainer(nextProcessStep);
                container.setVirtualMachine(newVM);
                nextProcessStep.setScheduledAtContainer(container);
                optimizationResult.addProcessStep(nextProcessStep);
            }
            else {
                VirtualMachine vm = runningVMs.get(0);
                if (vm.getDeployedContainers().size() == 0) {
                    Container container = getContainer(nextProcessStep);
                    container.setVirtualMachine(vm);
                    nextProcessStep.setScheduledAtContainer(container);
                    optimizationResult.addProcessStep(nextProcessStep);
                }
                else {
                    log.error("Several Container running on one vm:" + vm.getDeployedContainers().size());
                }
            }
        } catch(ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found");
            throw new ProblemNotSolvedException();
        } catch (Exception ex) {
            throw new ProblemNotSolvedException();
        }

        return optimizationResult;
    }

    private ProcessStep getMostUrgentProcessStep(List<WorkflowElement> nextWorkflowInstances) {

        for (WorkflowElement workflowElement : nextWorkflowInstances) {
            List<ProcessStep> nextSteps = getNextProcessStepsSorted(workflowElement);
            if (nextSteps.size() > 0) {
                return nextSteps.get(0);
            }
        }

        return null;
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
