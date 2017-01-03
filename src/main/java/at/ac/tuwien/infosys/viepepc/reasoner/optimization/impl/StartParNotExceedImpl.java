package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.registry.impl.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class StartParNotExceedImpl extends AbstractProvisioningImpl implements ProcessInstancePlacementProblem {

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

            for(WorkflowElement workflowElement : runningWorkflowInstances) {
                if (!vmStartedBecauseOfWorkflow.containsKey(workflowElement)) {
                    VirtualMachine vm = startNewVm(optimizationResult);
                    availableVms.add(vm);
                    vmStartedBecauseOfWorkflow.put(workflowElement, vm);
                }
            }

            if (availableVms.size() == 0) {
                availableVms.add(startNewVm(optimizationResult));
            }

            Iterator<VirtualMachine> iteratorAvailableVms = availableVms.iterator();
            while(iteratorAvailableVms.hasNext()) {
                VirtualMachine vm = iteratorAvailableVms.next();
                if (vm.getDeployedContainers().size() > 0) {
                    iteratorAvailableVms.remove();
                }
            }

            if (availableVms.size() == 0) {
                return optimizationResult;
            }

            availableVms.sort(Comparator.comparing(vm -> new Long(vm.getStartupTime())));

            List<VirtualMachine> alreadyUsedVms = new ArrayList<>();
            for(ProcessStep processStep : nextProcessSteps) {
                boolean deployed = false;
                for(VirtualMachine vm : availableVms) {
                    long remainingBTU = getRemainingLeasingDuration(new Date(), vm, optimizationResult);
                    if(remainingBTU > processStep.getExecutionTime()) {
                        if(!alreadyUsedVms.contains(vm)) {
                            deployContainerAssignProcessStep(processStep, vm, optimizationResult);
                            alreadyUsedVms.add(vm);
                        }
                        deployed = true;

                        break;
                    }
                }

                if(!deployed) {
                    VirtualMachine vm = startNewVMDeployContainerAssignProcessStep(processStep, optimizationResult);
                    alreadyUsedVms.add(vm);
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


    public long getRemainingLeasingDuration(Date tau_t, VirtualMachine vm, OptimizationResult optimizationResult) {
        Date startedAt = vm.getStartedAt();
        if (startedAt == null) {
            startedAt = tau_t;
        }
        Date toBeTerminatedAt = vm.getToBeTerminatedAt();
        if (toBeTerminatedAt == null) {
            toBeTerminatedAt = new Date(startedAt.getTime() + vm.getVmType().getLeasingDuration());
        }
        long remainingLeasingDuration = toBeTerminatedAt.getTime() - tau_t.getTime();

        for(ProcessStep processStep : optimizationResult.getProcessSteps()) {
            if(processStep.getScheduledAtVM() == vm || processStep.getScheduledAtContainer().getVirtualMachine() == vm) {
                remainingLeasingDuration = remainingLeasingDuration - processStep.getExecutionTime();
            }
        }

        if (remainingLeasingDuration < 0) {
            remainingLeasingDuration = 0;
        }
        return remainingLeasingDuration;

    }

}
