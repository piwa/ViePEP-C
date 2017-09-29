package at.ac.tuwien.infosys.viepepc.serviceexecutor;


import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.watchdog.Message;
import at.ac.tuwien.infosys.viepepc.watchdog.ServiceExecutionStatus;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Component
@Scope("prototype")
@Slf4j
public class ServiceExecution{

    @Autowired
    private ServiceInvoker serviceInvoker;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    @Value("${simulate}")
    private boolean simulate;

//    @Async
    public void startExecution(ProcessStep processStep, VirtualMachine virtualMachine) throws ServiceInvokeException {
        processStep.setStartDate(DateTime.now());
        log.info("Task-Start: " + processStep);

        inMemoryCache.getProcessStepsWaitingForServiceDone().put(processStep.getName(), processStep);

        if (simulate) {
            try {
                Thread.sleep(processStep.getExecutionTime());
            } catch (InterruptedException e) {
                log.error("EXCEPTION", e);
            }
        } else {
            serviceInvoker.invoke(virtualMachine, processStep);
        }
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Value("${messagebus.queue.name}")
    private String queueName;

//    @Async
	public void startExecution(ProcessStep processStep, Container container) throws ServiceInvokeException {
        processStep.setStartDate(DateTime.now());
		log.info("Task-Start: " + processStep);

        inMemoryCache.getProcessStepsWaitingForServiceDone().put(processStep.getName(), processStep);

        if (simulate) {
            try {
                Thread.sleep(processStep.getExecutionTime());
                Message message = new Message();
                message.setBody("Done");
                message.setProcessStepName(processStep.getName());
                message.setStatus(ServiceExecutionStatus.DONE);

                rabbitTemplate.convertAndSend(queueName, message);

            } catch (InterruptedException e) {
            }
        } else {
            serviceInvoker.invoke(container, processStep);
        }

	}

}
