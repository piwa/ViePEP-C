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

import java.util.*;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class AllParExceedContainerImpl extends AbstractProvisioningImpl implements ProcessInstancePlacementProblem {

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

            if (runningWorkflowInstances == null) {
                return optimizationResult;
            }

            removeAllBusyVms(availableVms);

            availableVms.sort(Comparator.comparingLong((VirtualMachine vm) -> new Long(getRemainingLeasingDurationIncludingScheduled(new Date(), vm, optimizationResult))).reversed());

            for(WorkflowElement workflowElement : runningWorkflowInstances) {

                List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(workflowElement);
                for(ProcessStep processStep : nextProcessSteps) {

                    boolean deployed = false;
                    Container container = getContainer(processStep);
                    for(VirtualMachine vm : availableVms) {
                        if (checkIfEnoughResourcesLeftOnVM(vm, container, optimizationResult)) {
                            deployContainerAssignProcessStep(processStep, vm, optimizationResult);
                            deployed = true;
                        }
                    }

                    if(!deployed) {
                        VirtualMachine vm = startNewVMDeployContainerAssignProcessStep(processStep, optimizationResult);
                        availableVms.add(vm);
                    }
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
