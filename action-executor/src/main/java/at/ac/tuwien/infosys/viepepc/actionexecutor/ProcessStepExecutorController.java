package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStepStatus;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.ServiceExecution;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvokeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ProcessStepExecutorController {

    @Autowired
    private ServiceExecution serviceExecution;

    @Async
    public void startProcessStepExecution(ProcessStep processStep) {

        processStep.setProcessStepStatus(ProcessStepStatus.DEPLOYING);

        try {
            serviceExecution.startExecution(processStep, processStep.getContainer());
        } catch (ServiceInvokeException e) {
            log.error("Exception while invoking service. Stop VM and reset.", e);
            reset(processStep);
        }
    }

    private void reset(ProcessStep processStep) {
        // TODO
//      cacheProcessStepService.getProcessStepsWaitingForExecution().remove(processStep);
//      cacheProcessStepService.getProcessStepsWaitingForServiceDone().remove(processStep.getName());
        processStep.reset();

    }

}
