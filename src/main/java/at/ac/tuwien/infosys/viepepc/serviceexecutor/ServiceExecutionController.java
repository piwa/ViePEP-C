package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepepc.configuration.ApplicationContext;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by philippwaibel on 18/05/16. edited by Gerta Sheganaku
 */
@Slf4j
@Component
public class ServiceExecutionController {

    @Autowired
    private WithVMDeploymentController withVMDeploymentController;
    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;
    @Autowired
    private ApplicationContext applicationContext;

    private Map<ProcessStep, ScheduledFuture<OnlyContainerDeploymentController>> processStepScheduledTasksMap = new ConcurrentHashMap<>();


    @Async//("serviceProcessExecuter")
    public void startInvocationViaVMs(List<ProcessStep> processSteps) {

        final Map<VirtualMachine, List<ProcessStep>> vmProcessStepsMap = new HashMap<>();
        for (final ProcessStep processStep : processSteps) {

            VirtualMachine scheduledAt = processStep.getScheduledAtVM();
            List<ProcessStep> processStepsOnVm = new ArrayList<>();
            if (vmProcessStepsMap.containsKey(scheduledAt)) {
                processStepsOnVm.addAll(vmProcessStepsMap.get(scheduledAt));
            }
            processStepsOnVm.add(processStep);
            vmProcessStepsMap.put(scheduledAt, processStepsOnVm);
        }

        for (final VirtualMachine virtualMachine : vmProcessStepsMap.keySet()) {

            final List<ProcessStep> processStepsOnVm = vmProcessStepsMap.get(virtualMachine);
            if (!virtualMachine.isLeased()) {
                withVMDeploymentController.leaseVMAndStartExecutionOnVirtualMachine(virtualMachine, processStepsOnVm);

            } else {
                withVMDeploymentController.startExecutionsOnVirtualMachine(vmProcessStepsMap.get(virtualMachine), virtualMachine);
            }
        }
    }

    @Async//("serviceProcessExecuter")
    public void startInvocationViaContainersOnVms(List<ProcessStep> processSteps) {

        final Map<VirtualMachine, Map<Container, List<ProcessStep>>> vmContainerProcessStepMap = new HashMap<>();
        final Map<Container, List<ProcessStep>> containerProcessStepsMap = createContainerProcessStepMap(processSteps);

        for (final Container container : containerProcessStepsMap.keySet()) {

            VirtualMachine scheduledAt = container.getVirtualMachine();
            if (scheduledAt == null) {
                log.error("No VM set for Container " + container);
            }
            if (!vmContainerProcessStepMap.containsKey(scheduledAt)) {
                vmContainerProcessStepMap.put(scheduledAt, new HashMap<>());
            }
            vmContainerProcessStepMap.get(scheduledAt).put(container, containerProcessStepsMap.get(container));
        }

        for (final VirtualMachine virtualMachine : vmContainerProcessStepMap.keySet()) {
            final Map<Container, List<ProcessStep>> containerProcessSteps = vmContainerProcessStepMap.get(virtualMachine);
            try {
                if (!virtualMachine.isLeased()) {
                    withVMDeploymentController.leaseVMAndStartExecutionOnContainer(virtualMachine, containerProcessSteps);

                } else {
                    withVMDeploymentController.startExecutionsOnContainer(vmContainerProcessStepMap.get(virtualMachine), virtualMachine);
                }
            } catch (Exception e) {
                log.error("Unable start invocation: " + e);
            }
        }
    }

    @Async
    public void startInvocationViaContainers(List<ProcessStep> processSteps) {

        for (ProcessStep processStep : processSteps) {

            if(processStepScheduledTasksMap.containsKey(processStep)) {
                boolean result = processStepScheduledTasksMap.get(processStep).cancel(false);
                if(!result) {
                    log.error("problem");
                }
                processStepScheduledTasksMap.remove(processStep);
            }

            OnlyContainerDeploymentController runnable = applicationContext.getOnlyContainerDeploymentController(processStep);
            ScheduledFuture scheduledFuture = taskScheduler.schedule(runnable, processStep.getScheduledStartedAt().toDate());
            processStepScheduledTasksMap.put(processStep, scheduledFuture);

        }

    }

    private Map<Container, List<ProcessStep>> createContainerProcessStepMap(List<ProcessStep> processSteps) {
        final Map<Container, List<ProcessStep>> containerProcessStepsMap = new HashMap<>();

        for (final ProcessStep processStep : processSteps) {
            Container scheduledAt = processStep.getScheduledAtContainer();
            if (!containerProcessStepsMap.containsKey(scheduledAt)) {
                containerProcessStepsMap.put(scheduledAt, new ArrayList<>());
            }
            containerProcessStepsMap.get(scheduledAt).add(processStep);
        }
        return containerProcessStepsMap;
    }
}