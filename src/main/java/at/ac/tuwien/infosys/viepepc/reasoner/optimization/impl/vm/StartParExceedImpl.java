package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.vm;

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

import java.util.*;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class StartParExceedImpl extends AbstractProvisioningImpl implements ProcessInstancePlacementProblem {

    private Map<WorkflowElement, VirtualMachine> vmStartedBecauseOfWorkflow = new HashMap<>();

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(Date tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResultImpl();

        try {
            placementHelper.setFinishedWorkflows();

            List<WorkflowElement> runningWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<VirtualMachine> availableVms = getRunningVms();
            List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(runningWorkflowInstances);

            if (nextProcessSteps == null) {
                return optimizationResult;
            }

            if(availableVms.size() < runningWorkflowInstances.size()) {
                for(int i = 0; i < runningWorkflowInstances.size() - availableVms.size(); i++) {
                    availableVms.add(startNewVm(optimizationResult));
                }
            }

            removeAllBusyVms(availableVms);

            if (availableVms.size() == 0) {
                return optimizationResult;
            }

            availableVms.sort(Comparator.comparing(vm -> new Long(vm.getStartupTime())));

            int usedVmCounter = 0;
            for(ProcessStep processStep : nextProcessSteps) {

                deployContainerAssignProcessStep(processStep, availableVms.get(usedVmCounter), optimizationResult);
                usedVmCounter = usedVmCounter + 1;
                if(usedVmCounter >= availableVms.size()) {
                    break;
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
