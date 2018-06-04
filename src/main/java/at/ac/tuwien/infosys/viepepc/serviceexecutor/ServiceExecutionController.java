package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepepc.configuration.ApplicationContext;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private ApplicationContext applicationContext;
    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 45000;

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

    private List<ProcessStep> processStepsToBeStarted = new ArrayList<>();

    public void startInvocationViaContainers(List<ProcessStep> processSteps) {
        for (ProcessStep processStep : processSteps) {
            OnlyContainerDeploymentController runnable = applicationContext.getOnlyContainerDeploymentController(processStep);
            threadPoolTaskExecutor.execute(runnable);
        }
    }

    //    @Async
    public void startTimedInvocationViaContainers(List<ProcessStep> processSteps) {
        synchronized (processStepsToBeStarted) {
            Set<ProcessStep> tempSet = new HashSet<>(processStepsToBeStarted);
            tempSet.addAll(processSteps);
            processStepsToBeStarted = new ArrayList<>(tempSet);
            processStepsToBeStarted.sort(Comparator.comparing(ProcessStep::getScheduledStartedAt));
        }

    }

    @Scheduled(initialDelay = 1000, fixedDelay = 3000)        // fixedRate
    private void scheduledProcessStarter() {

        synchronized (processStepsToBeStarted) {

            for (Iterator<ProcessStep> iterator = processStepsToBeStarted.iterator(); iterator.hasNext(); ) {
                ProcessStep processStep = iterator.next();

                DateTime startTimeWithContainer = processStep.getScheduledStartedAt().minus(onlyContainerDeploymentTime);
                if (startTimeWithContainer.minusSeconds(5).isBeforeNow()) {
                    OnlyContainerDeploymentController runnable = applicationContext.getOnlyContainerDeploymentController(processStep);
                    threadPoolTaskExecutor.execute(runnable);

                    iterator.remove();
                }
                else {
                    break;
                }
            }
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