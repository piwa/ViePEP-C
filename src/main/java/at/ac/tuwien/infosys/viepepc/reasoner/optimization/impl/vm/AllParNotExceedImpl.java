package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.vm;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.reasoner.Reasoning;
import at.ac.tuwien.infosys.viepepc.reasoner.impl.ReasoningImpl;
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
public class AllParNotExceedImpl extends AbstractProvisioningImpl implements ProcessInstancePlacementProblem {

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

            if (runningWorkflowInstances == null) {
                return optimizationResult;
            }

            removeAllBusyVms(availableVms);

            availableVms.sort(Comparator.comparingLong((VirtualMachine vm) -> new Long(getRemainingLeasingDurationIncludingScheduled(new Date(), vm, optimizationResult))).reversed());

            for (WorkflowElement workflowElement : runningWorkflowInstances) {

                List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(workflowElement);
                List<ProcessStep> runningProcessSteps = getRunningSteps(workflowElement);

                long remainingRunningProcessStepExecution = -1;
                Date now = new Date();
                for(ProcessStep processStep : runningProcessSteps) {
                    if(remainingRunningProcessStepExecution == -1 && remainingRunningProcessStepExecution < processStep.getRemainingExecutionTime(now)) {
                        remainingRunningProcessStepExecution = processStep.getRemainingExecutionTime(now);
                    }
                }

                long executionDurationFirstProcessStep = -1;
                for (ProcessStep processStep : nextProcessSteps) {

                    if (executionDurationFirstProcessStep == -1) {
                        executionDurationFirstProcessStep = processStep.getExecutionTime();
                    }

                    if (processStep.getExecutionTime() < executionDurationFirstProcessStep - ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS || processStep.getExecutionTime() < remainingRunningProcessStepExecution - ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS) {
                        long tau_t_1 = executionDurationFirstProcessStep - processStep.getExecutionTime();
                        if(optimizationResult.getTauT1() == -1 || optimizationResult.getTauT1() > tau_t_1) {
                            optimizationResult.setTauT1(tau_t_1);
                        }
                    }
                    else {
                        boolean deployed = false;
                        for (VirtualMachine vm : availableVms) {
                            long remainingBTU = getRemainingLeasingDurationIncludingScheduled(new Date(), vm, optimizationResult);
                            if (remainingBTU > processStep.getExecutionTime() && !vmAlreadyUsedInResult(vm, optimizationResult)) {
                                deployContainerAssignProcessStep(processStep, vm, optimizationResult);
                                deployed = true;
                                break;
                            }
                        }

                        if (!deployed) {
                            VirtualMachine vm = startNewVMDeployContainerAssignProcessStep(processStep, optimizationResult);
                            availableVms.add(vm);
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
