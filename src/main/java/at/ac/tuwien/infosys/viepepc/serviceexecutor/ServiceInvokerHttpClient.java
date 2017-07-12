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
import org.springframework.context.annotation.Scope;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * User: Philipp Hoenisch
 * Date: 2/10/14
 */
@Component
@Scope("prototype")
@Slf4j
public class ServiceInvokerHttpClient {

    private CloseableHttpClient httpclient = null;

    public ServiceInvokerHttpClient() {
        int timeout = 180;
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(timeout * 1000)
                .setConnectionRequestTimeout(timeout * 1000)
                .setSocketTimeout(timeout * 1000).build();
        httpclient = HttpClientBuilder.create().setDefaultRequestConfig(config).disableAutomaticRetries().build();
    }

    @Retryable(value = Exception.class, maxAttempts = 20, backoff=@Backoff(delay=1000, maxDelay=3000))
    public HttpResponse retryHttpGet(HttpGet httpGet, Stopwatch stopWatch) throws Exception {
        if(stopWatch.isRunning()) {
            stopWatch.reset();
        }
        log.info("Send " + httpGet.toString());
        stopWatch.start();
        HttpResponse response = httpclient.execute(httpGet);
        stopWatch.stop();
        return response;
    }

}
