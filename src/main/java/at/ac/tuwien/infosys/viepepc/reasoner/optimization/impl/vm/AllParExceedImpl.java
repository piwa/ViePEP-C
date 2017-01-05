package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.vm;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.reasoner.impl.ReasoningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AbstractProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.registry.impl.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.ContainerImageNotFoundException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class AllParExceedImpl extends AbstractProvisioningImpl implements ProcessInstancePlacementProblem {

    private Multimap<WorkflowElement, ProcessStep> waitingProcessSteps;

    public AllParExceedImpl() {
        waitingProcessSteps = ArrayListMultimap.create();
    }

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

            for (WorkflowElement workflowElement : runningWorkflowInstances) {

                List<ProcessStep> runningProcessSteps = getRunningSteps(workflowElement);
                List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(workflowElement);
                if (waitingProcessSteps.containsKey(workflowElement)) {
                    nextProcessSteps.addAll(waitingProcessSteps.get(workflowElement));
                }

                long remainingRunningProcessStepExecution = calcRemainingRunningProcessStepExecution(runningProcessSteps);
                long executionDurationFirstProcessStep = 0;
                if(nextProcessSteps.size() > 0) {
                    executionDurationFirstProcessStep = nextProcessSteps.get(0).getExecutionTime();
                }
                for(ProcessStep processStep : nextProcessSteps) {

                    if (processStep.getExecutionTime() < executionDurationFirstProcessStep - ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS || processStep.getExecutionTime() < remainingRunningProcessStepExecution - ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS) {
                        calcTauT1(optimizationResult, executionDurationFirstProcessStep, processStep);
                        waitingProcessSteps.put(workflowElement, processStep);
                    } else {

                        boolean deployed = false;
                        for (VirtualMachine vm : availableVms) {
                            if (!vmAlreadyUsedInResult(vm, optimizationResult)) {
                                deployContainerAssignProcessStep(processStep, vm, optimizationResult);
                                deployed = true;
                                break;
                            }
                        }

                        if (!deployed) {
                            VirtualMachine vm = startNewVMDeployContainerAssignProcessStep(processStep, optimizationResult);
                            availableVms.add(vm);
                        }
                        if (waitingProcessSteps.containsEntry(workflowElement, processStep)) {
                            waitingProcessSteps.remove(workflowElement, processStep);
                        }
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

}
