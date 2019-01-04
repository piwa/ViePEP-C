package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.ProvisioningSchedule;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Created by philippwaibel on 18/05/16. edited by Gerta Sheganaku
 */
@Slf4j
@Component
public class ActionExecutor {

//    @Autowired
//    private WithVMDeploymentController withVMDeploymentController;
//    @Autowired
//    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
//    @Autowired
//    private ActionExecutorConfiguration applicationContext;
//
//    private List<ProcessStep> processStepsToBeStarted = new ArrayList<>();

    @Autowired
    private ProvisioningSchedule provisioningSchedule;

    @Autowired
    private VMDeploymentController vmDeploymentController;
    @Autowired
    private ContainerDeploymentController containerDeploymentController;
    @Autowired
    private ProcessStepExecutorController processStepExecutorController;

    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 45000;





    @Scheduled(initialDelay = 1000, fixedDelay = 3000)        // fixedRate
    public void processProvisioningSchedule() {

        synchronized (provisioningSchedule) {

            for (Iterator<VirtualMachineInstance> iterator = provisioningSchedule.getVirtualMachineInstances().iterator(); iterator.hasNext(); ) {
                VirtualMachineInstance virtualMachineInstance = iterator.next();
                DateTime scheduledDeploymentStartTime = virtualMachineInstance.getScheduledCloudResourceUsage().getStart();
                if (scheduledDeploymentStartTime.minusSeconds(5).isBeforeNow()) {
                    try {
                        vmDeploymentController.deployVirtualMachine(virtualMachineInstance);
                    } catch (VmCouldNotBeStartedException e) {
                        log.error("EXCEPTION", e);
                    }
                    iterator.remove();
                }
            }

            for (Iterator<Container> iterator = provisioningSchedule.getContainers().iterator(); iterator.hasNext(); ) {
                Container container = iterator.next();
                DateTime scheduledDeploymentStartTime = container.getScheduledCloudResourceUsage().getStart();
                VirtualMachineInstance virtualMachineInstance = container.getVirtualMachineInstance();
                if (scheduledDeploymentStartTime.minusSeconds(5).isBeforeNow() && virtualMachineInstance.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYED)) {

                    containerDeploymentController.deployContainer(container);

                    iterator.remove();
                }
            }

            for (Iterator<ProcessStep> iterator = provisioningSchedule.getProcessSteps().iterator(); iterator.hasNext(); ) {
                ProcessStep processStep = iterator.next();
                DateTime scheduledStartTime = processStep.getScheduledStartDate();
                Container container = processStep.getContainer();
                if (scheduledStartTime.minusSeconds(5).isBeforeNow() && container.getContainerStatus().equals(VirtualMachineStatus.DEPLOYED)) {

                    processStepExecutorController.startProcessStepExecution(processStep);

                    iterator.remove();
                }
            }
        }
    }


















//
//
//
//
//
//
//
//
//    @Async
//    public void startInvocationViaContainersOnVms(List<ProcessStep> processSteps) {
//
//        final Map<VirtualMachineInstance, Map<Container, List<ProcessStep>>> vmContainerProcessStepMap = new HashMap<>();
//        final Map<Container, List<ProcessStep>> containerProcessStepsMap = createContainerProcessStepMap(processSteps);
//
//        for (final Container container : containerProcessStepsMap.keySet()) {
//
//            VirtualMachineInstance scheduledAt = container.getVirtualMachineInstance();
//            if (scheduledAt == null) {
//                log.error("No VM set for Container " + container);
//            }
//            if (!vmContainerProcessStepMap.containsKey(scheduledAt)) {
//                vmContainerProcessStepMap.put(scheduledAt, new HashMap<>());
//            }
//            vmContainerProcessStepMap.get(scheduledAt).put(container, containerProcessStepsMap.get(container));
//        }
//
//        for (final VirtualMachineInstance virtualMachineInstance : vmContainerProcessStepMap.keySet()) {
//            final Map<Container, List<ProcessStep>> containerProcessSteps = vmContainerProcessStepMap.get(virtualMachineInstance);
//            try {
//                if (!virtualMachineInstance.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYED)) {
//                    withVMDeploymentController.leaseVMAndStartExecutionOnContainer(virtualMachineInstance, containerProcessSteps);
//
//                } else {
//                    withVMDeploymentController.startExecutionsOnContainer(vmContainerProcessStepMap.get(virtualMachineInstance), virtualMachineInstance);
//                }
//            } catch (Exception e) {
//                log.error("Unable start invocation: " + e);
//            }
//        }
//    }
//
//    public void startTimedInvocationViaContainers(List<ProcessStep> processSteps) {
//        synchronized (processStepsToBeStarted) {
//            Set<ProcessStep> tempSet = new HashSet<>(processStepsToBeStarted);
//            tempSet.addAll(processSteps);
//            processStepsToBeStarted = new ArrayList<>(tempSet);
//            processStepsToBeStarted.sort(Comparator.comparing(ProcessStep::getScheduledStartDate));
//        }
//
//    }
//
//    @Scheduled(initialDelay = 1000, fixedDelay = 3000)        // fixedRate
//    public void scheduledProcessStarter() {
//
//        synchronized (processStepsToBeStarted) {
//
//            for (Iterator<ProcessStep> iterator = processStepsToBeStarted.iterator(); iterator.hasNext(); ) {
//                ProcessStep processStep = iterator.next();
//
//                DateTime startTimeWithContainer = processStep.getScheduledStartDate().minus(onlyContainerDeploymentTime);
//                if (startTimeWithContainer.minusSeconds(5).isBeforeNow()) {
//                    OnlyContainerDeploymentController runnable = applicationContext.getOnlyContainerDeploymentController(processStep);
//                    threadPoolTaskExecutor.execute(runnable);
//
//                    iterator.remove();
//                } else {
//                    break;
//                }
//            }
//        }
//    }
//
//
//    private Map<Container, List<ProcessStep>> createContainerProcessStepMap(List<ProcessStep> processSteps) {
//        final Map<Container, List<ProcessStep>> containerProcessStepsMap = new HashMap<>();
//
//        for (final ProcessStep processStep : processSteps) {
//            Container scheduledAt = processStep.getContainer();
//            if (!containerProcessStepsMap.containsKey(scheduledAt)) {
//                containerProcessStepsMap.put(scheduledAt, new ArrayList<>());
//            }
//            containerProcessStepsMap.get(scheduledAt).add(processStep);
//        }
//        return containerProcessStepsMap;
//    }
}