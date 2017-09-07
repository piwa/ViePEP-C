package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.dto.InvocationResultDTO;
import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * User: Philipp Hoenisch
 * Date: 2/10/14
 */
@Component
@Scope("prototype")
@Slf4j
public class ServiceInvoker {

    @Autowired
    private ServiceInvokerHttpClient serviceInvokerHttpClient;

    @Value("${spring.rabbitmq.host}")
    private String mqIp;

    /**
     * @param url to be invoked
     * @return the http body as entity including the response time and http status code
     */
    public void invoke(String url) {


        Stopwatch stopWatch = Stopwatch.createUnstarted();

        try {
            serviceInvokerHttpClient.retryHttpGet(url, stopWatch);


//            String result = EntityUtils.toString(response.getEntity());
//            long elapsed = stopWatch.elapsed(TimeUnit.MILLISECONDS);
//            invocationResult.setResult(result);
//            invocationResult.setExecutionTime(elapsed);
//            invocationResult.setStatus(response.getStatusLine().getStatusCode());
        } catch (Exception e) {
            log.error("Exception", e);
//            invocationResult.setStatus(404);
//            invocationResult.setResult(e.getMessage());
        }
//        return invocationResult;
    }


    public void invoke(VirtualMachine virtualMachine, ProcessStep processStep) {
        String task = processStep.getServiceType().getName().replace("service", "");
        String uri = createURI(virtualMachine, "8080", task, processStep.getName());
        invoke(uri);
    }

	public void invoke(Container container, ProcessStep processStep) {
		String task = processStep.getServiceType().getName().replace("service", "");
        task = task.replace("Service", "");
        String uri = createURI(container.getVirtualMachine(), container.getExternPort(), task, processStep.getName());
        invoke(uri);
	}

	private String createURI(VirtualMachine vm, String port, String task, String processStepName) {
        mqIp = mqIp.replace(".", "_");
        return vm.getURI().concat(":"+port).concat("/" + task).concat("/" + processStepName).concat("/normal").concat("/nodata").concat("/" + mqIp);
    }


}
