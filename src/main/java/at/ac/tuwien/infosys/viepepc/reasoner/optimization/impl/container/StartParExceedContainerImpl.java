package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.container;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AbstractProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

import java.util.*;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class StartParExceedContainerImpl extends AbstractProvisioningImpl implements ProcessInstancePlacementProblem {

    private Map<WorkflowElement, VirtualMachine> vmStartedBecauseOfWorkflow = new HashMap<>();

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResultImpl();

        try {
            placementHelper.setFinishedWorkflows();

            List<WorkflowElement> runningWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<VirtualMachine> availableVms = getRunningVms();
            List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(runningWorkflowInstances);

            if (nextProcessSteps == null || nextProcessSteps.size() == 0) {
                return optimizationResult;
            }

/*
            if(availableVms.size() < runningWorkflowInstances.size()) {
                int newVMs = runningWorkflowInstances.size() - availableVms.size();
                for(int i = 0; i < newVMs; i++) {
                    VirtualMachine vm = startNewDefaultVm(optimizationResult);
                    availableVms.add(vm);
                    optimizationResult.addVirtualMachine(vm);
                }
            }

            if (availableVms.size() == 0) {
                return optimizationResult;
            }
*/
            availableVms.sort(Comparator.comparing(VirtualMachine::getStartupTime));

            for(ProcessStep processStep : nextProcessSteps) {
                Container container = getContainer(processStep);

                boolean deployed = false;

                for(VirtualMachine vm : availableVms) {
                    if(checkIfEnoughResourcesLeftOnVM(vm, container, optimizationResult)) {
                        deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                        deployed = true;
                        break;
                    }
                }
                if(!deployed && availableVms.size() < runningWorkflowInstances.size()) {
                    VirtualMachine vm = startNewDefaultVm(optimizationResult);
                    availableVms.add(vm);
                    deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                    availableVms.sort(Comparator.comparing(VirtualMachine::getStartupTime));
                }
            }
        } catch(ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found", ex);
            throw new ProblemNotSolvedException();
        } catch (Exception ex) {
            log.error("EXCEPTION", ex);
            throw new ProblemNotSolvedException();
        }

        return optimizationResult;
    }

}
