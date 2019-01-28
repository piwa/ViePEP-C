package at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class ServiceInvokerImpl implements ServiceInvoker {

    @Autowired
    private ServiceInvokerHttpClient serviceInvokerHttpClient;

    @Override
    public void invoke(Container container, ProcessStep processStep) throws ServiceInvokeException {
        String task = processStep.getServiceType().getName().replace("service", "");
        task = task.replace("Service", "");
        String uri;
        if (container.getVirtualMachineInstance() != null) {
            uri = createURI(container.getVirtualMachineInstance().getURI(), container.getExternPort(), task, processStep.getName());
        } else {
            uri = createURI(container.getIpAddress(), container.getExternPort(), task, processStep.getName());
        }
        try {
            serviceInvokerHttpClient.retryHttpGet(uri);
        } catch (Exception e) {
            throw new ServiceInvokeException(e);
        }
    }

    private String createURI(String uri, String port, String task, String processStepName) {
        return uri.concat(":" + port).concat("/" + task).concat("/" + processStepName).concat("/normal").concat("/nodata");
    }


}
