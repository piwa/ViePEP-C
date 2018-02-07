package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.frincu.vm;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.frincu.AbstractVMProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.NoVmFoundException;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

import java.util.List;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class OneVMforAllImpl extends AbstractVMProvisioningImpl implements ProcessInstancePlacementProblem {


    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResultImpl();

        try {
            placementHelper.setFinishedWorkflows();

            List<WorkflowElement> nextWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<VirtualMachine> runningVMs = getRunningVms();
            List<ProcessStep> runningProcessSteps = getAllRunningSteps(nextWorkflowInstances);
            ProcessStep nextProcessStep = getMostUrgentProcessStep(nextWorkflowInstances);

            if (nextProcessStep == null || runningProcessSteps.size() > 0) {
                return optimizationResult;
            }
            if (runningVMs.size() == 0) {                           // start new vm, deploy container and start first service
                startNewVMDeployContainerAssignProcessStep(nextProcessStep, optimizationResult);
            }
            else {
                VirtualMachine vm = runningVMs.get(0);
                if (vm.getDeployedContainers().size() == 0) {
                    deployContainerAssignProcessStep(nextProcessStep, vm, optimizationResult);
                }
                else {
                    log.error("Several Container running on one vm:" + vm.getDeployedContainers().size());
                }
            }
        } catch(ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found");
            throw new ProblemNotSolvedException();
        } catch (NoVmFoundException | Exception ex) {
            throw new ProblemNotSolvedException();
        }

        return optimizationResult;
    }


}
