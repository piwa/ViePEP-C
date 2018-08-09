package at.ac.tuwien.infosys.viepepc.watchdog;

import at.ac.tuwien.infosys.viepepc.database.entities.Action;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import at.ac.tuwien.infosys.viepepc.reasoner.Reasoning;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
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
    private PlacementHelper placementHelper;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    @Lazy
    private Reasoning reasoning;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private TaskExecutor workflowDoneTaskExecutor;
    @Autowired
    private ReportDaoService reportDaoService;

    @RabbitListener(queues = "${messagebus.queue.name}")
    public void receiveMessage(@Payload Message message) {
        try {
            log.debug(message.toString());
            if (message.getStatus().equals(ServiceExecutionStatus.DONE)) {
                ProcessStep processStep = inMemoryCache.getProcessStepsWaitingForServiceDone().get(message.getProcessStepName());
                if (processStep != null) {
                    finaliseSuccessfullExecution(processStep);
                    inMemoryCache.getProcessStepsWaitingForServiceDone().remove(message.getProcessStepName());
                }
            }
            else {
                log.warn("Service throw an exception: " + message.getBody());
                ProcessStep processStep = inMemoryCache.getProcessStepsWaitingForServiceDone().get(message.getProcessStepName());
                resetContainerAndProcessStep(processStep.getScheduledAtContainer().getVirtualMachine(), processStep, "Service");
            }
        } catch (Exception ex) {
            log.error("Exception in receive message method", ex);
        }
    }

    private void resetContainerAndProcessStep(VirtualMachine vm, ProcessStep processStep, String reason) {
        ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), processStep.getScheduledAtContainer().getName(), vm.getInstanceId(), Action.FAILED, reason);
        reportDaoService.save(reportContainer);

        inMemoryCache.getProcessStepsWaitingForServiceDone().remove(processStep.getName());
        inMemoryCache.getWaitingForExecutingProcessSteps().remove(processStep);
        processStep.getScheduledAtContainer().shutdownContainer();
        processStep.reset();
    }

    private void finaliseSuccessfullExecution(ProcessStep processStep) throws Exception {
        DateTime finishedAt = new DateTime();
        processStep.setFinishedAt(finishedAt);

        log.info("Task-Done: " + processStep);
        workflowDoneTaskExecutor.execute(() -> {
            if (processStep.getScheduledAtContainer() != null) {
//            synchronized (processStep.getScheduledAtContainer()) {
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
                    placementHelper.stopContainer(processStep.getScheduledAtContainer());
                }
//            }
            }


            if (processStep.isLastElement()) {

                Random random = new Random();
                int counter = 0;
                while (counter < 100) {
                    WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(processStep.getWorkflowName());

                    if (workflowElement == null || workflowElement.getFinishedAt() != null) {
                        break;
                    }
                    synchronized (workflowElement) {
                        List<ProcessStep> runningSteps = placementHelper.getRunningProcessSteps(processStep.getWorkflowName());
                        List<ProcessStep> nextSteps = placementHelper.getNextSteps(processStep.getWorkflowName());
                        if ((nextSteps == null || nextSteps.isEmpty()) && (runningSteps == null || runningSteps.isEmpty())) {
                            try {
                                workflowElement.setFinishedAt(finishedAt);
                            } catch (Exception e) {
                                log.error("Exception while try to finish workflow: " + workflowElement, e);
                            }

                            cacheWorkflowService.deleteRunningWorkflowInstance(workflowElement);
                            log.info("Workflow done. Workflow: " + workflowElement);
                            break;
                        }

                        log.debug("Waiting for the end of workflow: " + workflowElement.toStringWithoutElements());
                        try {
                            TimeUnit.MILLISECONDS.sleep(random.nextInt(10000));
                        } catch (InterruptedException e) {
                        }
                        counter = counter + 1;
                    }

                    if (counter >= 90) {
                        workflowElement = cacheWorkflowService.getWorkflowById(processStep.getWorkflowName());
                        if (workflowElement == null) {
                            log.debug("Had to wait to long for process end; But workflow is now null");
                        } else {
                            log.debug("Had to wait to long for process end; Workflow: " + workflowElement.toStringWithoutElements());
                        }
                    }
                }
            }
        });


        reasoning.setNextOptimizeTimeNow();

    }

}
