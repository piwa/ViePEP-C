package at.ac.tuwien.infosys.viepepc.scheduler.frincu.vm;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.frincu.AbstractVMProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.SchedulerAlgorithm;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.exceptions.NoVmFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.exceptions.ProblemNotSolvedException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
@Component
@Profile("AllParNotExceed")
public class AllParNotExceedImpl extends AbstractVMProvisioningImpl implements SchedulerAlgorithm {

    private Multimap<WorkflowElement, ProcessStep> waitingProcessSteps;

    public AllParNotExceedImpl() {
        waitingProcessSteps = ArrayListMultimap.create();
    }

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

            if (runningWorkflowInstances == null || runningWorkflowInstances.size() == 0) {
                return optimizationResult;
            }

            int usedVmCounter = availableVms.size();

            removeAllBusyVms(availableVms, runningWorkflowInstances);
            availableVms.sort(Comparator.comparingLong((VirtualMachine vm) -> getRemainingLeasingDurationIncludingScheduled(new DateTime(), vm, optimizationResult)).reversed());

            int amountOfParallelTasks = 0;
            for (WorkflowElement workflowElement : runningWorkflowInstances) {
                int parallelTasks = getNextProcessStepsSorted(workflowElement).size() + getRunningSteps(workflowElement).size();
                if (parallelTasks == 0) {
                    amountOfParallelTasks = amountOfParallelTasks + 1;
                } else {
                    amountOfParallelTasks = amountOfParallelTasks + parallelTasks;
                }
            }

            for (WorkflowElement workflowElement : runningWorkflowInstances) {

//                List<ProcessStep> runningProcessSteps = getRunningSteps(workflowElement);
                List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(workflowElement);
//                if (waitingProcessSteps.containsKey(workflowElement)) {
//                    nextProcessSteps.addAll(waitingProcessSteps.get(workflowElement));
//                    nextProcessSteps.sort(Comparator.comparingLong(ProcessStep::getExecutionTime).reversed());
//                }

//                long remainingRunningProcessStepExecution = calcRemainingRunningProcessStepExecution(runningProcessSteps);
//                long executionDurationFirstProcessStep = 0;
//                if(nextProcessSteps.size() > 0) {
//                    executionDurationFirstProcessStep = nextProcessSteps.get(0).getExecutionTime();
//                }
                for (ProcessStep processStep : nextProcessSteps) {

//                    if ((processStep.getExecutionTime() < executionDurationFirstProcessStep - ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS || processStep.getExecutionTime() < remainingRunningProcessStepExecution - ReasoningImpl.MIN_TAU_T_DIFFERENCE_MS) && availableVms.size() == 0) {
//                        if(!waitingProcessSteps.containsEntry(workflowElement, processStep)) {
//                            calcTauT1(optimizationResult, executionDurationFirstProcessStep, processStep);
//                            waitingProcessSteps.put(workflowElement, processStep);
//                        }
//                    }
//                    else {
                    VirtualMachine deployedVM = null;
                    boolean deployed = false;
                    for (VirtualMachine vm : availableVms) {
                        long remainingBTU = getRemainingLeasingDurationIncludingScheduled(new DateTime(), vm, optimizationResult);
                        Container container = getContainer(processStep);
                        if (remainingBTU > (processStep.getExecutionTime() + container.getContainerImage().getDeployTime())) {
                            deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                            deployed = true;
                            deployedVM = vm;
                            break;
                        }
                    }

                    if (!deployed && usedVmCounter < amountOfParallelTasks) {
                        try {
                            startNewVMDeployContainerAssignProcessStep(processStep, optimizationResult);
                            usedVmCounter = usedVmCounter + 1;
                        } catch (NoVmFoundException e) {
                            log.error("Could not find a VM. Postpone execution.");
                        }
                    }
                    if (deployed) {
                        availableVms.remove(deployedVM);
                    }

//                        if(waitingProcessSteps.containsEntry(workflowElement, processStep)) {
//                            waitingProcessSteps.remove(workflowElement, processStep);
//                        }
//                    }
                }
            }

        } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found", ex);
            throw new ProblemNotSolvedException();
        } catch (Exception ex) {
            log.error("EXCEPTION", ex);
            throw new ProblemNotSolvedException();
        }

//        inMemoryCache.getProcessStepsWaitingForExecution().addAll(optimizationResult.getProcessSteps());

        return optimizationResult;
    }

    @Override
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return new AsyncResult<>(optimize(tau_t));
    }


}
