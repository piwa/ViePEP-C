package at.ac.tuwien.infosys.viepepc.serviceexecutor;


import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheProcessStepService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStepStatus;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvokeException;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvoker;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Component
//@Scope("prototype")
@Slf4j
public class ServiceExecution {

    @Autowired
    private ServiceInvoker serviceInvoker;

    @Value("${simulate}")
    private boolean simulate;

    public void startExecution(ProcessStep processStep, Container container) throws ServiceInvokeException {
        processStep.setProcessStepStatus(ProcessStepStatus.DEPLOYING);
        processStep.setStartDate(DateTime.now());
        log.info("Task-Start: " + processStep);

        processStep.setProcessStepStatus(ProcessStepStatus.RUNNING);

        serviceInvoker.invoke(container, processStep);
    }

}
