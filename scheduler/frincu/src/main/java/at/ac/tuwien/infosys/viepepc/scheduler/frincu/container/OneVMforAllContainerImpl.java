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

import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
@Component
@Profile("OneVMforAllContainer")
public class OneVMforAllContainerImpl extends AbstractContainerProvisioningImpl implements SchedulerAlgorithm {


    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResult();

        try {
            workflowUtilities.setFinishedWorkflows();

            List<WorkflowElement> runningWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<VirtualMachine> runningVMs = getRunningVms();
//            List<ProcessStep> runningProcessSteps = getAllRunningSteps(runningWorkflowInstances);
            List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(runningWorkflowInstances);

            if (nextProcessSteps == null || nextProcessSteps.size() == 0) {
                return optimizationResult;
            }

            VirtualMachine vm = null;
            if (runningVMs != null && runningVMs.size() > 0) {
                vm = runningVMs.get(0);
            }
            else {
                vm = startNewDefaultVm(optimizationResult);
            }

            for (ProcessStep processStep : nextProcessSteps) {
                Container container = getContainer(processStep);
                if (checkIfEnoughResourcesLeftOnVM(vm, container, optimizationResult)) {
                    deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                }
            }

        } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found");
            throw new ProblemNotSolvedException();
        } catch (NoVmFoundException e) {
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
