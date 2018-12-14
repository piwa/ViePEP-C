package at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.frincu.container;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.frincu.AbstractContainerProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.exceptions.NoVmFoundException;
import at.ac.tuwien.infosys.viepepc.reasoner.frincu.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.scheduling.annotation.AsyncResult;

import java.util.*;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class StartParNotExceedContainerImpl extends AbstractContainerProvisioningImpl implements ProcessInstancePlacementProblem {

    private Map<WorkflowElement, VirtualMachine> vmStartedBecauseOfWorkflow = new HashMap<>();

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResultImpl();

        try {
            workflowUtilities.setFinishedWorkflows();

            List<WorkflowElement> runningWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<VirtualMachine> availableVms = getRunningVms();
            List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(runningWorkflowInstances);

            if (nextProcessSteps == null || nextProcessSteps.size() == 0) {
                return optimizationResult;
            }


//            if(availableVms.size() < runningWorkflowInstances.size()) {
//                int newVMs = runningWorkflowInstances.size() - availableVms.size();
//                for(int i = 0; i < newVMs; i++) {
//                    VirtualMachine vm = startNewDefaultVm(optimizationResult);
//                    availableVms.add(vm);
//                    optimizationResult.addVirtualMachine(vm);
//                }
//            }

            if (availableVms.size() == 0) {
                availableVms.add(startNewDefaultVm(optimizationResult));
            }


            availableVms.sort(Comparator.comparing(VirtualMachine::getStartupTime));

            for (ProcessStep processStep : nextProcessSteps) {

                boolean deployed = false;
                Container container = getContainer(processStep);
                for (VirtualMachine vm : availableVms) {
                    long remainingBTU = getRemainingLeasingDuration(new DateTime(), vm);
                    if (remainingBTU > (processStep.getExecutionTime() + container.getContainerImage().getDeployTime())) {
                        if (checkIfEnoughResourcesLeftOnVM(vm, container, optimizationResult)) {
                            deployed = true;
                            deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                            break;
                        }
                    }
                }

                if (!deployed && availableVms.size() < runningWorkflowInstances.size()) {
                    try {
                        VirtualMachine vm = startNewVMDeployContainerAssignProcessStep(processStep, optimizationResult);
                        availableVms.add(vm);
                    } catch (NoVmFoundException e) {
                        log.error("Could not find a VM. Postpone execution.");
                    }

                }
            }
        } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found", ex);
            throw new ProblemNotSolvedException();
        } catch (NoVmFoundException | Exception ex) {
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
