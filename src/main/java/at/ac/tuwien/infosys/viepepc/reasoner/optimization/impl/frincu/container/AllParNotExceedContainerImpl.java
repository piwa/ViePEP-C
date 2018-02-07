package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.frincu.container;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.frincu.AbstractContainerProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.NoVmFoundException;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

import java.util.*;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class AllParNotExceedContainerImpl extends AbstractContainerProvisioningImpl implements ProcessInstancePlacementProblem {

    private Multimap<WorkflowElement, ProcessStep> waitingProcessSteps;

    public AllParNotExceedContainerImpl() {
        waitingProcessSteps =  ArrayListMultimap.create();
    }

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

            if (runningWorkflowInstances == null || runningWorkflowInstances.size() == 0) {
                return optimizationResult;
            }

//            removeAllBusyVms(availableVms);
            availableVms.sort(Comparator.comparingLong((VirtualMachine vm) -> getRemainingLeasingDurationIncludingScheduled(new DateTime(), vm, optimizationResult)).reversed());

            int amountOfParallelTasks = 0;
            for (WorkflowElement workflowElement : runningWorkflowInstances) {
                amountOfParallelTasks = amountOfParallelTasks + getNextProcessStepsSorted(workflowElement).size();
            }

            for(WorkflowElement workflowElement : runningWorkflowInstances) {

                List<ProcessStep> runningProcessSteps = getRunningSteps(workflowElement);
                List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(workflowElement);
                if(waitingProcessSteps.containsKey(workflowElement)) {
                    nextProcessSteps.addAll(waitingProcessSteps.get(workflowElement));
                }

                long remainingRunningProcessStepExecution = calcRemainingRunningProcessStepExecution(runningProcessSteps);
                long executionDurationFirstProcessStep = 0;
                if(nextProcessSteps.size() > 0) {
                    executionDurationFirstProcessStep = nextProcessSteps.get(0).getExecutionTime();
                }
                for(ProcessStep processStep : nextProcessSteps) {

//                    if ((processStep.getExecutionTime() < executionDurationFirstProcessStep - ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS || processStep.getExecutionTime() < remainingRunningProcessStepExecution - ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS) && availableVms.size() == 0) {
//                        if(!waitingProcessSteps.containsEntry(workflowElement, processStep)) {
//                            calcTauT1(optimizationResult, executionDurationFirstProcessStep, processStep);
//                            waitingProcessSteps.put(workflowElement, processStep);
//                        }
//                    }
//                    else {
                        boolean deployed = false;
                        Container container = getContainer(processStep);
                        for (VirtualMachine vm : availableVms) {
                            long remainingBTU = getRemainingLeasingDuration(new DateTime(), vm);
                            if (remainingBTU > (processStep.getExecutionTime() + container.getContainerImage().getDeployTime())) {
                                if (checkIfEnoughResourcesLeftOnVM(vm, container, optimizationResult)) {
                                    deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                                    deployed = true;
                                    break;
                                }
                            }
                        }

                        if (!deployed && availableVms.size() < amountOfParallelTasks) {

                            try {
                                VirtualMachine vm = startNewVMDeployContainerAssignProcessStep(processStep, optimizationResult);
                                availableVms.add(vm);
                            } catch (NoVmFoundException e) {
                                log.error("Could not find a VM. Postpone execution.");
                            }

                        }

//                        if(waitingProcessSteps.containsEntry(workflowElement, processStep)) {
//                            waitingProcessSteps.remove(workflowElement, processStep);
//                        }
//                    }
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
