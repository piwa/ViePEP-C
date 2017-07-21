package at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.container;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheContainerService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.ProcessInstancePlacementProblem;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AbstractContainerProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.AbstractProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.OptimizationResultImpl;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.NoVmFoundException;
import at.ac.tuwien.infosys.viepepc.reasoner.optimization.impl.exceptions.ProblemNotSolvedException;
import at.ac.tuwien.infosys.viepepc.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.registry.impl.container.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
public class OneVMPerTaskContainerImpl extends AbstractContainerProvisioningImpl implements ProcessInstancePlacementProblem {

    @Autowired
    protected PlacementHelper placementHelper;
    @Autowired
    protected CacheWorkflowService cacheWorkflowService;
    @Autowired
    protected CacheContainerService cacheContainerService;
    @Autowired
    protected CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    protected ContainerImageRegistryReader containerImageRegistryReader;

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResultImpl();

        try {
            List<WorkflowElement> nextWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(nextWorkflowInstances);
            List<VirtualMachine> runningVMs = getRunningVms();

            if (nextProcessSteps == null || nextProcessSteps.size() == 0) {
                return optimizationResult;
            }

            for(ProcessStep processStep : nextProcessSteps) {
                boolean deployed = false;
                Container container = getContainer(processStep);
                for(VirtualMachine vm : runningVMs) {
                    if (checkIfEnoughResourcesLeftOnVM(vm, container, optimizationResult)) {
                        deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                        deployed = true;
                        break;
                    }
                }
                if(!deployed) {
                    try {
                        runningVMs.add(startNewVMDeployContainerAssignProcessStep(processStep, container, optimizationResult));
                    } catch (NoVmFoundException e) {
                        log.error("Could not find a VM. Postpone execution.");
                    }
                }
            }
        } catch(ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found");
            throw new ProblemNotSolvedException();
        } catch (Exception ex) {
            throw new ProblemNotSolvedException();
        }

        return optimizationResult;
    }



}
