package at.ac.tuwien.infosys.viepepc.watchdog;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ActionExecutorUtilities;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.Reasoning;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class Receiver {

    @Autowired
    private ActionExecutorUtilities actionExecutorUtilities;
    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    @Lazy
    private Reasoning reasoning;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    @Value("${optimization.after.task.done}")
    private boolean optimizationAfterTaskDone = false;

    @RabbitListener(queues = "${messagebus.queue.name}")
    public void receiveMessage(@Payload Message message) {
        try {
            log.debug(message.toString());
            if (message.getStatus().equals(ServiceExecutionStatus.DONE)) {
                ProcessStep processStep = inMemoryCache.getProcessStepsWaitingForServiceDone().get(message.getProcessStepName());
                if (processStep != null) {
                    finaliseSuccessfulExecution(processStep);
                    inMemoryCache.getProcessStepsWaitingForServiceDone().remove(message.getProcessStepName());
                }
            }
        } catch (Exception ex) {
            log.error("Exception in receive message method", ex);
        }
    }


    private void finaliseSuccessfulExecution(ProcessStep processStep) throws Exception {
        DateTime finishedAt = new DateTime();
        processStep.setFinishedAt(finishedAt);

        log.info("Task-Done: " + processStep);





        if(processStep.getScheduledAtContainer() != null) {
            synchronized (processStep.getScheduledAtContainer()) {
                List<ProcessStep> processSteps = new ArrayList<>();
                placementHelper.getRunningSteps().stream().filter(ele -> ((ProcessStep) ele) != processStep).forEach(element -> processSteps.add((ProcessStep) element));
                processSteps.addAll(placementHelper.getNotStartedUnfinishedSteps().stream().filter(ps -> ps.getScheduledAtContainer() != null).collect(Collectors.toList()));

                boolean stillNeeded = false;
                for (ProcessStep tmp : processSteps) {
                    if (tmp.getScheduledAtContainer() != null && tmp != processStep && tmp.getScheduledAtContainer().getContainerID().equals(processStep.getScheduledAtContainer().getContainerID())) {
                        stillNeeded = true;
                        break;
                    }
                }

                if (!stillNeeded) {
                    actionExecutorUtilities.stopContainer(processStep.getScheduledAtContainer());
                }
            }
        }

        if (processStep.isLastElement()) {

            Random random = new Random();
            for(int i = 0; i < 10; i++) {           // TODO is maybe not needed anymore, due to the synchronized

                WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(processStep.getWorkflowName());
                synchronized (workflowElement) {
                    List<ProcessStep> runningSteps = placementHelper.getRunningProcessSteps(processStep.getWorkflowName());
                    List<ProcessStep> nextSteps = placementHelper.getNextSteps(processStep.getWorkflowName());
                    log.info("Try to finish workflow. RunningSteps=" + printProcessSteps(runningSteps) + "; nextSteps=" + printProcessSteps(nextSteps));
                    if ((nextSteps == null || nextSteps.isEmpty()) && (runningSteps == null || runningSteps.isEmpty())) {
                        WorkflowElement workflowById = cacheWorkflowService.getWorkflowById(processStep.getWorkflowName());
                        try {
                            workflowById.setFinishedAt(finishedAt);
                        } catch (Exception e) {
                            log.error("Exception while try to finish workflow: " + workflowById, e);
                        }

                        cacheWorkflowService.deleteRunningWorkflowInstance(workflowById);
                        log.info("Workflow done. Workflow: " + workflowById);
                        break;
                    }

                    try {
                        TimeUnit.MILLISECONDS.sleep(random.nextInt(10000));
                    } catch (Exception e) {
                    }
                }
            }

        }
        if(optimizationAfterTaskDone) {
            reasoning.setNextOptimizeTimeNow();
        }

    }

    private String printProcessSteps(List<ProcessStep> processSteps) {

        StringBuilder builder = new StringBuilder();
        processSteps.forEach(ps -> builder.append(ps.toString()).append(" "));
        return builder.toString();

    }
}
