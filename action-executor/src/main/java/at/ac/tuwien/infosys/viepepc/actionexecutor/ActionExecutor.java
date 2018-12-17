package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
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

    @Autowired
    private WithVMDeploymentController withVMDeploymentController;
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private ActionExecutorConfiguration applicationContext;

    private List<ProcessStep> processStepsToBeStarted = new ArrayList<>();

    @Value("${only.container.deploy.time}")
    private long onlyContainerDeploymentTime = 45000;


    @Async
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

    public void startTimedInvocationViaContainers(List<ProcessStep> processSteps) {
        synchronized (processStepsToBeStarted) {
            Set<ProcessStep> tempSet = new HashSet<>(processStepsToBeStarted);
            tempSet.addAll(processSteps);
            processStepsToBeStarted = new ArrayList<>(tempSet);
            processStepsToBeStarted.sort(Comparator.comparing(ProcessStep::getScheduledStartedAt));
        }

    }

    @Scheduled(initialDelay = 1000, fixedDelay = 3000)        // fixedRate
    public void scheduledProcessStarter() {

        synchronized (processStepsToBeStarted) {

            for (Iterator<ProcessStep> iterator = processStepsToBeStarted.iterator(); iterator.hasNext(); ) {
                ProcessStep processStep = iterator.next();

                DateTime startTimeWithContainer = processStep.getScheduledStartedAt().minus(onlyContainerDeploymentTime);
                if (startTimeWithContainer.minusSeconds(5).isBeforeNow()) {
                    OnlyContainerDeploymentController runnable = applicationContext.getOnlyContainerDeploymentController(processStep);
                    threadPoolTaskExecutor.execute(runnable);

                    iterator.remove();
                } else {
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