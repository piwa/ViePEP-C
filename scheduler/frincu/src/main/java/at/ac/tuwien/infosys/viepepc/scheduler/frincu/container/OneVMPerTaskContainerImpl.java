package at.ac.tuwien.infosys.viepepc.scheduler.frincu.container;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.library.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.frincu.AbstractContainerProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.SchedulerAlgorithm;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.exceptions.NoVmFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.exceptions.ProblemNotSolvedException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
@Component
@Profile("OneVMPerTaskContainer")
public class OneVMPerTaskContainerImpl extends AbstractContainerProvisioningImpl implements SchedulerAlgorithm {

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
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResult();

        try {
            List<WorkflowElement> nextWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(nextWorkflowInstances);
            List<VirtualMachine> runningVMs = getRunningVms();

            if (nextProcessSteps == null || nextProcessSteps.size() == 0) {
                return optimizationResult;
            }

            for (ProcessStep processStep : nextProcessSteps) {
                boolean deployed = false;
                Container container = getContainer(processStep);
                for (VirtualMachine vm : runningVMs) {
                    if (checkIfEnoughResourcesLeftOnVM(vm, container, optimizationResult)) {
                        deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                        deployed = true;
                        break;
                    }
                }
                if (!deployed) {
                    try {
                        runningVMs.add(startNewVMDeployContainerAssignProcessStep(processStep, container, optimizationResult));
                    } catch (NoVmFoundException e) {
                        log.error("Could not find a VM. Postpone execution.");
                    }
                }
            }
        } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found");
            throw new ProblemNotSolvedException();
        } catch (Exception ex) {
            throw new ProblemNotSolvedException();
        }

        return optimizationResult;
    }

    @Override
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return new AsyncResult<>(optimize(tau_t));
    }


}
