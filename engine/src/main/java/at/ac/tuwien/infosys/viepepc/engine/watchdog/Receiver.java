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
    }

}
