package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.vm;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AbstractProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AbstractVMProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.NoVmFoundException;
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
public class StartParExceedImpl extends AbstractVMProvisioningImpl implements ProcessInstancePlacementProblem {

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

//            if(availableVms.size() < runningWorkflowInstances.size()) {
//                int newVMs = runningWorkflowInstances.size() - availableVms.size();
//                for(int i = 0; i < newVMs; i++) {
//                    VirtualMachine vm = startNewDefaultVm(optimizationResult);
//                    availableVms.add(vm);
////                    optimizationResult.addVirtualMachine(vm);
//                }
//            }

            removeAllBusyVms(availableVms, runningWorkflowInstances);

//            if (availableVms.size() == 0) {
//                return optimizationResult;
//            }

            availableVms.sort(Comparator.comparing(VirtualMachine::getStartupTime));

            int usedVmCounter = availableVms.size();
            for(ProcessStep processStep : nextProcessSteps) {


                if(availableVms.size() > 0) {
                    VirtualMachine deployedVm = availableVms.get(0);
                    deployContainerAssignProcessStep(processStep, deployedVm, optimizationResult);
                    availableVms.remove(deployedVm);
                    usedVmCounter = usedVmCounter + 1;
                }
                else if(usedVmCounter < runningWorkflowInstances.size()){
                    try {
                        startNewVMDeployContainerAssignProcessStep(processStep, optimizationResult);
                        usedVmCounter = usedVmCounter + 1;
                    } catch (NoVmFoundException e) {
                        log.error("Could not find a VM. Postpone execution.");
                    }

                }

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
