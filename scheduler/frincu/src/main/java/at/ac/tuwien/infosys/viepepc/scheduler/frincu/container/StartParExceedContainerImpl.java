package at.ac.tuwien.infosys.viepepc.scheduler.frincu.container;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.frincu.AbstractContainerProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.SchedulerAlgorithm;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.exceptions.NoVmFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.exceptions.ProblemNotSolvedException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
@Component
@Profile("StartParExceedContainer")
public class StartParExceedContainerImpl extends AbstractContainerProvisioningImpl implements SchedulerAlgorithm {

    private Map<WorkflowElement, VirtualMachine> vmStartedBecauseOfWorkflow = new HashMap<>();

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResult();

        try {
            workflowUtilities.setFinishedWorkflows();

            List<WorkflowElement> runningWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<VirtualMachine> availableVms = getRunningVms();
            List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(runningWorkflowInstances);

            if (nextProcessSteps == null || nextProcessSteps.size() == 0) {
                return optimizationResult;
            }

            availableVms.sort(Comparator.comparing(VirtualMachine::getStartupTime));

            for (ProcessStep processStep : nextProcessSteps) {
                Container container = getContainer(processStep);

                boolean deployed = false;

                for (VirtualMachine vm : availableVms) {
                    if (checkIfEnoughResourcesLeftOnVM(vm, container, optimizationResult)) {
                        deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                        deployed = true;
                        break;
                    }
                }
                if (!deployed && availableVms.size() < runningWorkflowInstances.size()) {

                    try {
                        VirtualMachine vm = startNewVmDefaultOrForContainer(optimizationResult, container.getContainerConfiguration());
                        deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                        availableVms.add(vm);
                        availableVms.sort(Comparator.comparing(VirtualMachine::getStartupTime));
                    } catch (NoVmFoundException e) {
                        log.error("Could not find a VM. Postpone execution.");
                    }

                }
            }
        } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found", ex);
            throw new ProblemNotSolvedException();
        } catch (Exception ex) {
            log.error("EXCEPTION", ex);
            throw new ProblemNotSolvedException();
        }

        return optimizationResult;
    }

    @Override
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return new AsyncResult<>(optimize(tau_t));
    }

}
