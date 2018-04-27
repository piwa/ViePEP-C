package at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.watchdog.Message;
import at.ac.tuwien.infosys.viepepc.watchdog.ServiceExecutionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

//@Component
//@Scope("prototype")
@Slf4j
public class ServiceInvokerSimulation implements ServiceInvoker {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Value("${messagebus.queue.name}")
    private String queueName;

    private void invoke(ProcessStep processStep) throws ServiceInvokeException {
        try {
            Thread.sleep(processStep.getExecutionTime());
            Message message = new Message();
            message.setBody("Done");
            message.setProcessStepName(processStep.getName());
            message.setStatus(ServiceExecutionStatus.DONE);

            rabbitTemplate.convertAndSend(queueName, message);

        } catch (Exception e) {
            throw new ServiceInvokeException(e);
        }
    }

    @Override
    public void invoke(VirtualMachine virtualMachine, ProcessStep processStep) throws ServiceInvokeException {
        invoke(processStep);
    }

    @Override
    public void invoke(Container container, ProcessStep processStep) throws ServiceInvokeException {
        invoke(processStep);
    }

}
