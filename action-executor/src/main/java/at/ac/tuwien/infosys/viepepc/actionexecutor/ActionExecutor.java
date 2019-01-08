package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.ProvisioningSchedule;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheProcessStepService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStepStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 18/05/16. edited by Gerta Sheganaku
 */
@Slf4j
@Component
public class ActionExecutor {

    @Autowired
    private ProvisioningSchedule provisioningSchedule;
    @Autowired
    private CacheProcessStepService cacheProcessStepService;
    @Autowired
    private VMDeploymentController vmDeploymentController;
    @Autowired
    private ContainerDeploymentController containerDeploymentController;
    @Autowired
    private ProcessStepExecutorController processStepExecutorController;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private WorkflowUtilities workflowUtilities;

    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 45000;


    @Scheduled(initialDelay = 1000, fixedDelay = 1000)        // fixedRate
    public void processProvisioningSchedule() {

        synchronized (provisioningSchedule) {

            for (Map.Entry<UUID, VirtualMachineInstance> entry : provisioningSchedule.getVirtualMachineInstancesMap().entrySet()) {
                VirtualMachineInstance virtualMachineInstance = entry.getValue();
                DateTime scheduledDeploymentStartTime = virtualMachineInstance.getScheduledCloudResourceUsage().getStart();
                if (scheduledDeploymentStartTime.minusSeconds(3).isBeforeNow() && virtualMachineInstance.getVirtualMachineStatus().equals(VirtualMachineStatus.SCHEDULED)) {
                    vmDeploymentController.deploy(virtualMachineInstance);
                }
            }

            for (Map.Entry<UUID, Container> entry : provisioningSchedule.getContainersMap().entrySet()) {
                Container container = entry.getValue();
                DateTime scheduledDeploymentStartTime = container.getScheduledCloudResourceUsage().getStart();
                VirtualMachineInstance virtualMachineInstance = container.getVirtualMachineInstance();
                if (scheduledDeploymentStartTime.minusSeconds(3).isBeforeNow() &&
                        container.getContainerStatus().equals(ContainerStatus.SCHEDULED) &&
                        virtualMachineInstance.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYED)) {
                    containerDeploymentController.deploy(container);
                }
            }

            for (Map.Entry<UUID, ProcessStep> entry : provisioningSchedule.getProcessStepsMap().entrySet()) {
                ProcessStep processStep = entry.getValue();
                DateTime scheduledStartTime = processStep.getScheduledStartDate();
                Container container = processStep.getContainer();
                if (scheduledStartTime.minusSeconds(3).isBeforeNow() &&
                        processStep.getProcessStepStatus().equals(ProcessStepStatus.SCHEDULED) &&
                        container.getContainerStatus().equals(ContainerStatus.DEPLOYED)) {
                    processStepExecutorController.startProcessStepExecution(processStep);
                }
            }


            for (Iterator<Map.Entry<UUID, ProcessStep>> iterator = provisioningSchedule.getProcessStepsMap().entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<UUID, ProcessStep> entry = iterator.next();
                ProcessStep processStep = entry.getValue();

                if (processStep.getFinishedAt() != null) {
                    iterator.remove();
                    if (processStep.isLastElement()) {
                        checkIfWorkflowDone(processStep);
                    }
                }
            }

            for (Iterator<Map.Entry<UUID, Container>> iterator = provisioningSchedule.getContainersMap().entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<UUID, Container> entry = iterator.next();
                Container container = entry.getValue();
                DateTime scheduledDeploymentEndTime = container.getScheduledCloudResourceUsage().getEnd();

                if (scheduledDeploymentEndTime.isBeforeNow() && !containerStillNeeded(container)) {
                    containerDeploymentController.terminate(container);
                    iterator.remove();
                }
            }

            for (Iterator<Map.Entry<UUID, VirtualMachineInstance>> iterator = provisioningSchedule.getVirtualMachineInstancesMap().entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<UUID, VirtualMachineInstance> entry = iterator.next();
                VirtualMachineInstance virtualMachineInstance = entry.getValue();
                DateTime scheduledDeploymentEndTime = virtualMachineInstance.getScheduledCloudResourceUsage().getEnd();

                if (scheduledDeploymentEndTime.isBeforeNow() && !vmStillNeeded(virtualMachineInstance)) {
                    vmDeploymentController.terminate(virtualMachineInstance);
                    iterator.remove();
                }
            }
        }
    }

    private void checkIfWorkflowDone(ProcessStep processStep) {
        WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(processStep.getWorkflowName());

        if (workflowElement == null || workflowElement.getFinishedAt() != null) {
            return;
        }
        List<ProcessStep> runningSteps = workflowUtilities.getRunningProcessSteps(processStep.getWorkflowName());
        List<ProcessStep> nextSteps = workflowUtilities.getNextSteps(processStep.getWorkflowName());
        if ((nextSteps == null || nextSteps.isEmpty()) && (runningSteps == null || runningSteps.isEmpty())) {
            try {
                workflowElement.setFinishedAt(DateTime.now());
            } catch (Exception e) {
                log.error("Exception while try to finish workflow: " + workflowElement, e);
            }

            cacheWorkflowService.deleteRunningWorkflowInstance(workflowElement);
            log.info("Workflow done. Workflow: " + workflowElement);
            return;
        }


    }

    private boolean vmStillNeeded(VirtualMachineInstance virtualMachineInstance) {

        for (Container deployedContainer : virtualMachineInstance.getDeployedContainers()) {
            if (!deployedContainer.getContainerStatus().equals(ContainerStatus.TERMINATED)) {
                return true;
            }
        }
        return false;
    }

    private boolean containerStillNeeded(Container container) {
        List<ProcessStep> processStepsWaitingForServiceDoneMap = cacheProcessStepService.getRunningProcessSteps();
        for (ProcessStep processStep : processStepsWaitingForServiceDoneMap) {
            if (processStep.getContainer().equals(container)) {
                return true;
            }
        }
        return false;
    }

}