package at.ac.tuwien.infosys.viepepc.watchdog;

import at.ac.tuwien.infosys.viepepc.configuration.MessagingConfiguration;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ProcessStepElementRepository;
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

import java.util.List;

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
    private ProcessStepElementRepository processStepElementRepository;

    @RabbitListener(queues = "${messagebus.queue.name}")
    public void receiveMessage(@Payload Message message) {
        if(message.getStatus().equals(ServiceExecutionStatus.DONE)) {
            ProcessStep processStep = processStepElementRepository.findOne(message.getProcessStepId());
            finaliseExecution(processStep);
        }
    }


    private void finaliseExecution(ProcessStep processStep) {
        DateTime finishedAt = new DateTime();
        processStep.setFinishedAt(finishedAt);

        log.info("Task-Done: " + processStep);

        if(processStep.getScheduledAtContainer() != null) {
            placementHelper.stopContainer(processStep.getScheduledAtContainer());
        }

        if (processStep.isLastElement()) {

            List<ProcessStep> runningSteps = placementHelper.getRunningProcessSteps(processStep.getWorkflowName());
            List<ProcessStep> nextSteps = placementHelper.getNextSteps(processStep.getWorkflowName());
            if ((nextSteps == null || nextSteps.isEmpty()) && (runningSteps == null || runningSteps.isEmpty())) {
                WorkflowElement workflowById = cacheWorkflowService.getWorkflowById(processStep.getWorkflowName());
                try {
                    workflowById.setFinishedAt(finishedAt);
                } catch (Exception e) {
                }

                cacheWorkflowService.deleteRunningWorkflowInstance(workflowById);
                log.info("Workflow done. Workflow: " + workflowById);
            }
        }
        reasoning.setNextOptimizeTimeNow();

    }

}
