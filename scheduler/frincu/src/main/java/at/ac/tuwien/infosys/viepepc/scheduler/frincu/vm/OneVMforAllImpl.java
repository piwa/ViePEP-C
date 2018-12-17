package at.ac.tuwien.infosys.viepepc.scheduler.frincu.vm;

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
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
@Component
@Profile("OneVMforAll")
public class OneVMforAllImpl extends AbstractVMProvisioningImpl implements SchedulerAlgorithm {

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResult();

        try {
            workflowUtilities.setFinishedWorkflows();

            List<WorkflowElement> nextWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<VirtualMachine> runningVMs = getRunningVms();
            List<ProcessStep> runningProcessSteps = getAllRunningSteps(nextWorkflowInstances);
            ProcessStep nextProcessStep = getMostUrgentProcessStep(nextWorkflowInstances);

            if (nextProcessStep == null || runningProcessSteps.size() > 0) {
                return optimizationResult;
            }
            if (runningVMs.size() == 0) {                           // start new vm, deploy container and start first service
                startNewVMDeployContainerAssignProcessStep(nextProcessStep, optimizationResult);
            } else {
                VirtualMachine vm = runningVMs.get(0);
                if (vm.getDeployedContainers().size() == 0) {
                    deployContainerAssignProcessStep(nextProcessStep, vm, optimizationResult);
                } else {
                    log.error("Several Container running on one vm:" + vm.getDeployedContainers().size());
                }
            }
        } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found");
            throw new ProblemNotSolvedException();
        } catch (NoVmFoundException | Exception ex) {
            throw new ProblemNotSolvedException();
        }

        return optimizationResult;
    }

    @Override
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return null;
    }


}
