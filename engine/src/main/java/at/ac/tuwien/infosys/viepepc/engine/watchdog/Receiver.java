package at.ac.tuwien.infosys.viepepc.engine.watchdog;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.ActionExecutorUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheProcessStepService;
import at.ac.tuwien.infosys.viepepc.library.entities.Action;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStepStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.library.Message;
import at.ac.tuwien.infosys.viepepc.library.ServiceExecutionStatus;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class Receiver {

    @Autowired
    private WorkflowUtilities workflowUtilities;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private CacheProcessStepService cacheProcessStepService;
    @Autowired
    private TaskExecutor workflowDoneTaskExecutor;
    @Autowired
    private ReportDaoService reportDaoService;

    @RabbitListener(queues = "${messagebus.queue.name}")
    public void receiveMessage(@Payload Message message) {

        log.debug(message.toString());

        Optional<ProcessStep> processStepOptional = cacheProcessStepService.getRunningProcessStep(message.getProcessStepName());
        if (processStepOptional.isPresent()) {
            if (message.getStatus().equals(ServiceExecutionStatus.DONE)) {
                processStepOptional.ifPresent(processStep -> {
                    try {
                        finaliseSuccessfulExecution(processStep);
                    } catch (Exception ex) {
                        log.error("Exception in receive message method", ex);
                    }
                });
            } else {
                log.warn("Service throw an exception: ProcessStep=" + message.getProcessStepName() + ",Exception=" + message.getBody());
                processStepOptional.ifPresent(processStep -> resetContainerAndProcessStep(processStep.getContainer().getVirtualMachineInstance(), processStep, "Service"));
            }
        }
    }

    private void resetContainerAndProcessStep(VirtualMachineInstance vm, ProcessStep processStep, String reason) {
        ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), processStep.getContainer().getName(), processStep.getContainer().getContainerConfiguration().getName(), vm.getInstanceId(), Action.FAILED, reason);
        reportDaoService.save(reportContainer);

        processStep.setProcessStepStatus(ProcessStepStatus.EXCEPTION);
        processStep.getContainer().shutdownContainer();
        processStep.reset();
    }

    private void finaliseSuccessfulExecution(ProcessStep processStep) throws Exception {
        DateTime finishedAt = new DateTime();
        processStep.setProcessStepStatus(ProcessStepStatus.DONE);
        processStep.setFinishedAt(finishedAt);

        log.info("Task-Done: " + processStep);
//        workflowDoneTaskExecutor.execute(() -> {
////            if (processStep.get() != null) {
//////            synchronized (processStep.get()) {
////                List<ProcessStep> processSteps = new ArrayList<>();
////                workflowUtilities.getRunningSteps().stream().filter(ele -> ((ProcessStep) ele) != processStep).forEach(element -> processSteps.add((ProcessStep) element));
////                processSteps.addAll(workflowUtilities.getNotStartedUnfinishedSteps().stream().filter(ps -> ps.get() != null).collect(Collectors.toList()));
////
////                boolean stillNeeded = false;
////                for (ProcessStep tmp : processSteps) {
////                    if (tmp.get() != null && tmp != processStep && tmp.get().getContainerID().equals(processStep.get().getContainerID())) {
////                        stillNeeded = true;
////                        break;
////                    }
////                }
////
////                if (!stillNeeded) {
////                    actionExecutorUtilities.stopContainer(processStep.get());
////                }
//////            }
////            }
//
//
//            if (processStep.isLastElement()) {
//
//                Random random = new Random();
//                int counter = 0;
//                while (counter < 100) {
//                    WorkflowElement workflowElement = cacheWorkflowService.getWorkflowById(processStep.getWorkflowName());
//
//                    if (workflowElement == null || workflowElement.getFinishedAt() != null) {
//                        break;
//                    }
//                    synchronized (workflowElement) {
//                        List<ProcessStep> runningSteps = workflowUtilities.getRunningProcessSteps(processStep.getWorkflowName());
//                        List<ProcessStep> nextSteps = workflowUtilities.getNextSteps(processStep.getWorkflowName());
//                        if ((nextSteps == null || nextSteps.isEmpty()) && (runningSteps == null || runningSteps.isEmpty())) {
//                            try {
//                                workflowElement.setFinishedAt(finishedAt);
//                            } catch (Exception e) {
//                                log.error("Exception while try to finish workflow: " + workflowElement, e);
//                            }
//
//                            cacheWorkflowService.deleteRunningWorkflowInstance(workflowElement);
//                            log.info("Workflow done. Workflow: " + workflowElement);
//                            break;
//                        }
//
//                        log.debug("Waiting for the end of workflow: " + workflowElement.toStringWithoutElements());
//                        try {
//                            TimeUnit.MILLISECONDS.sleep(random.nextInt(10000));
//                        } catch (InterruptedException e) {
//                        }
//                        counter = counter + 1;
//                    }
//
//                    if (counter >= 90) {
//                        workflowElement = cacheWorkflowService.getWorkflowById(processStep.getWorkflowName());
//                        if (workflowElement == null) {
//                            log.debug("Had to wait to long for process end; But workflow is now null");
//                        } else {
//                            log.debug("Had to wait to long for process end; Workflow: " + workflowElement.toStringWithoutElements());
//                        }
//                    }
//                }
//            }
//        });
    }

    private String printProcessSteps(List<ProcessStep> processSteps) {

        StringBuilder builder = new StringBuilder();
        processSteps.forEach(ps -> builder.append(ps.toString()).append(" "));
        return builder.toString();

    }
}
