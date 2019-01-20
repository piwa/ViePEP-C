package at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.Message;
import at.ac.tuwien.infosys.viepepc.library.ServiceExecutionStatus;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

//@Component
//@Scope("prototype")
@Slf4j
public class ServiceInvokerSimulation implements ServiceInvoker {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Value("${messagebus.queue.name}")
    private String queueName;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Override
    public void invoke(Container container, ProcessStep processStep) throws ServiceInvokeException {
        invoke(processStep);
    }

    private void invoke(ProcessStep processStep) throws ServiceInvokeException {
        try {

            DateTime endTime = DateTime.now().plus(processStep.getExecutionTime());

            taskScheduler.schedule(() -> {
                Message message = new Message();
                message.setBody("Done");
                message.setProcessStepName(processStep.getName());
                message.setStatus(ServiceExecutionStatus.DONE);

                rabbitTemplate.convertAndSend(queueName, message);
            }, endTime.toDate());


        } catch (Exception e) {
            throw new ServiceInvokeException(e);
        }
    }

}
