package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.reasoning.service.dto.InvocationResultDTO;
import com.google.common.base.Stopwatch;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * User: Philipp Hoenisch
 * Date: 2/10/14
 */
@Component
@Scope("prototype")
public class ServiceInvoker {
    private CloseableHttpClient httpclient = null;

    public ServiceInvoker() {
        int timeout = 180;
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(timeout * 1000)
                .setConnectionRequestTimeout(timeout * 1000)
                .setSocketTimeout(timeout * 1000).build();
        httpclient = HttpClientBuilder.create().setDefaultRequestConfig(config).disableAutomaticRetries().build();
    }

    /**
     * @param url to be invoked
     * @return the http body as entity including the response time and http status code
     */
    public InvocationResultDTO invoke(String url) {
        InvocationResultDTO invocationResult = new InvocationResultDTO();

        HttpGet httpGet = new HttpGet(url);
        Stopwatch stopWatch = Stopwatch.createUnstarted();

        HttpResponse response = null;
        try {
            stopWatch.start();

            response = httpclient.execute(httpGet);

            String result = EntityUtils.toString(response.getEntity());
            long elapsed = stopWatch.elapsed(TimeUnit.MILLISECONDS);
            stopWatch.stop();
            invocationResult.setResult(result);
            invocationResult.setExecutionTime(elapsed);
            invocationResult.setStatus(response.getStatusLine().getStatusCode());
        } catch (Exception e) {
            invocationResult.setStatus(404);
            invocationResult.setResult(e.getMessage());
        }
        return invocationResult;

    }

    public InvocationResultDTO invoke(VirtualMachine virtualMachine, ProcessStep processSteps) {
        String task = processSteps.getServiceType().getName().replace("service", "");
        String uri = virtualMachine.getURI().concat(":8080").concat("/service/").concat(task).concat("/normal").concat("/nodata");
        try {
            return invoke(uri);
        } catch (Exception ex) {
            InvocationResultDTO invocationResult = new InvocationResultDTO();
            invocationResult.setStatus(404);
            invocationResult.setResult(ex.getMessage());
            return invocationResult;
        }
    }

	public InvocationResultDTO invoke(DockerContainer container, ProcessStep processStep) {
		VirtualMachine vm = container.getVirtualMachine();
		int port = container.getDockerImage().getExternPort();
				
		String task = processStep.getServiceType().getName().replace("service", "");
        String uri = vm.getURI().concat(":"+port).concat("/service/").concat(task).concat("/normal").concat("/nodata");
        try {
            return invoke(uri);
        } catch (Exception ex) {
            InvocationResultDTO invocationResult = new InvocationResultDTO();
            invocationResult.setStatus(404);
            invocationResult.setResult(ex.getMessage());
            return invocationResult;
        }
	}
}
