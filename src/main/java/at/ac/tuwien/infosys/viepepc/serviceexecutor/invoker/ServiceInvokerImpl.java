package at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class ServiceInvokerImpl implements ServiceInvoker {

    @Autowired
    private ServiceInvokerHttpClient serviceInvokerHttpClient;

    /**
     * @param url to be invoked
     * @return the http body as entity including the response time and http status code
     */
    private void invoke(String url) throws ServiceInvokeException {
        Stopwatch stopWatch = Stopwatch.createUnstarted();
        try {
            serviceInvokerHttpClient.retryHttpGet(url, stopWatch);
        } catch (Exception e) {
            throw new ServiceInvokeException(e);
        }
    }


    @Override
    public void invoke(VirtualMachine virtualMachine, ProcessStep processStep) throws ServiceInvokeException {
        String task = processStep.getServiceType().getName().replace("service", "");
        String uri = createURI(virtualMachine, "8080", task, processStep.getName());
        invoke(uri);
    }

	@Override
    public void invoke(Container container, ProcessStep processStep) throws ServiceInvokeException {
		String task = processStep.getServiceType().getName().replace("service", "");
        task = task.replace("Service", "");
        String uri = createURI(container.getVirtualMachine(), container.getExternPort(), task, processStep.getName());
        invoke(uri);
	}

	private String createURI(VirtualMachine vm, String port, String task, String processStepName) {
        return vm.getURI().concat(":"+port).concat("/" + task).concat("/" + processStepName).concat("/normal").concat("/nodata");
    }


}
