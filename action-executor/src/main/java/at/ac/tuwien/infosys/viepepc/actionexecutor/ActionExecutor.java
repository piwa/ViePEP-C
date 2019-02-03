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
import lombok.Getter;
import lombok.Setter;
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

    private boolean pauseTermination = false;

    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 45000;

    public void pauseTermination() {
        pauseTermination = true;
    }
    public void unpauseTermination() {
        pauseTermination = false;
    }

    @Scheduled(initialDelay = 2000, fixedDelay = 2000)        // fixedRate
    public void processProvisioningSchedule() {

        synchronized (provisioningSchedule) {

            // Perform start events
            for (VirtualMachineInstance virtualMachineInstance : provisioningSchedule.getVirtualMachineInstancesMap().values()) {
                DateTime scheduledDeploymentStartTime = virtualMachineInstance.getScheduledCloudResourceUsage().getStart();
                if (scheduledDeploymentStartTime.minusSeconds(3).isBeforeNow() && virtualMachineInstance.getVirtualMachineStatus().equals(VirtualMachineStatus.SCHEDULED)) {
                    vmDeploymentController.deploy(virtualMachineInstance);
                }
            }

            for (Container container : provisioningSchedule.getContainersMap().values()) {
                DateTime scheduledDeploymentStartTime = container.getScheduledCloudResourceUsage().getStart();
                VirtualMachineInstance virtualMachineInstance = container.getVirtualMachineInstance();
                if (scheduledDeploymentStartTime.minusSeconds(3).isBeforeNow() &&
                        container.getContainerStatus().equals(ContainerStatus.SCHEDULED) && virtualMachineInstance.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYED)) {
                    containerDeploymentController.deploy(container);
                }
            }

            for (ProcessStep processStep : provisioningSchedule.getProcessStepsMap().values()) {
                DateTime scheduledStartTime = processStep.getScheduledStartDate();
                Container container = processStep.getContainer();
                if (scheduledStartTime.minusSeconds(3).isBeforeNow() &&
                        processStep.getProcessStepStatus().equals(ProcessStepStatus.SCHEDULED) && container.getContainerStatus().equals(ContainerStatus.DEPLOYED)) {
                    processStepExecutorController.startProcessStepExecution(processStep);
                }
            }

            // Perform termination events
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

            if(!pauseTermination) {
                for (Iterator<Map.Entry<UUID, Container>> iterator = provisioningSchedule.getContainersMap().entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry<UUID, Container> entry = iterator.next();
                    Container container = entry.getValue();
                    DateTime scheduledDeploymentEndTime = container.getScheduledCloudResourceUsage().getEnd();

                    if (container.getContainerStatus().equals(ContainerStatus.DEPLOYED) && scheduledDeploymentEndTime.isBeforeNow() && !containerStillNeeded(container)) {
                        containerDeploymentController.terminate(container);
                        iterator.remove();
                    }
                }

                for (Iterator<Map.Entry<UUID, VirtualMachineInstance>> iterator = provisioningSchedule.getVirtualMachineInstancesMap().entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry<UUID, VirtualMachineInstance> entry = iterator.next();
                    VirtualMachineInstance virtualMachineInstance = entry.getValue();
                    DateTime scheduledDeploymentEndTime = virtualMachineInstance.getScheduledCloudResourceUsage().getEnd();

                    if (virtualMachineInstance.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYED) && scheduledDeploymentEndTime.isBeforeNow() && !vmStillNeeded(virtualMachineInstance)) {
                        vmDeploymentController.terminate(virtualMachineInstance);
                        iterator.remove();
                    }
                }
            }
        }
    }

    public void checkIfWorkflowDone(ProcessStep processStep) {
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