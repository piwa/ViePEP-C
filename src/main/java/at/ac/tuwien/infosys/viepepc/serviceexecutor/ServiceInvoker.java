package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.dto.InvocationResultDTO;
import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.io.IOException;
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


    /**
     * @param url to be invoked
     * @return the http body as entity including the response time and http status code
     */
    public InvocationResultDTO invoke(String url) {
        InvocationResultDTO invocationResult = new InvocationResultDTO();

        HttpGet httpGet = new HttpGet(url);
        Stopwatch stopWatch = Stopwatch.createUnstarted();

        HttpResponse response;
        try {
            response = serviceInvokerHttpClient.retryHttpGet(httpGet, stopWatch);
            String result = EntityUtils.toString(response.getEntity());
            long elapsed = stopWatch.elapsed(TimeUnit.MILLISECONDS);
            invocationResult.setResult(result);
            invocationResult.setExecutionTime(elapsed);
            invocationResult.setStatus(response.getStatusLine().getStatusCode());
        } catch (Exception e) {
            log.error("Exception", e);
            invocationResult.setStatus(404);
            invocationResult.setResult(e.getMessage());
        }
        return invocationResult;
    }


    public InvocationResultDTO invoke(VirtualMachine virtualMachine, ProcessStep processSteps) {
        String task = processSteps.getServiceType().getName().replace("service", "");
        String uri = virtualMachine.getURI().concat(":8080").concat("/service/").concat(task).concat("/normal").concat("/nodata");
        return invoke(uri);
    }

	public InvocationResultDTO invoke(Container container, ProcessStep processStep) {
		VirtualMachine vm = container.getVirtualMachine();
		String port = container.getExternPort();
				
		String task = processStep.getServiceType().getName().replace("service", "");
        task = task.replace("Service", "");
        String uri = vm.getURI().concat(":"+port).concat("/service/").concat(task).concat("/normal").concat("/nodata");

        return invoke(uri);
	}


}
