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
import at.ac.tuwien.infosys.viepepc.registry.impl.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class OneVMforAllContainerImpl extends AbstractProvisioningImpl implements ProcessInstancePlacementProblem {


    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(Date tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResultImpl();

        try {
            placementHelper.setFinishedWorkflows();

            List<WorkflowElement> runningWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<VirtualMachine> runningVMs = getRunningVms();
            List<ProcessStep> runningProcessSteps = getAllRunningSteps(runningWorkflowInstances);
            List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(runningWorkflowInstances);

            if (runningProcessSteps.size() > 0 || nextProcessSteps == null) {
                return optimizationResult;
            }

            VirtualMachine vm = null;
            if (runningVMs != null && runningVMs.size() > 0) {
                vm = runningVMs.get(0);
            }
            else {
                vm = startNewVm(optimizationResult);
            }

            for (ProcessStep processStep : nextProcessSteps) {
                Container container = getContainer(processStep);
                if (checkIfEnoughResourcesLeftOnVM(vm, container, optimizationResult)) {
                    deployContainerAssignProcessStep(processStep, vm, optimizationResult);
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


}
