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
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * User: Philipp Hoenisch
 * Date: 2/10/14
 */
@Component
@Scope("prototype")
@Slf4j
public class ServiceInvokerHttpClient {

    @Retryable(value = Exception.class, maxAttempts = 20, backoff=@Backoff(delay=1000, maxDelay=3000))
    public HttpStatus retryHttpGet(String url, Stopwatch stopWatch) throws Exception {

        if(stopWatch.isRunning()) {
            stopWatch.reset();
        }
        log.info("Send " + url);
        stopWatch.start();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        HttpEntity<String> entity = new HttpEntity<String>(headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        stopWatch.stop();

        if(!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new Exception("Exception while sending GET: " + url + "; Status Code: " + responseEntity.getStatusCodeValue());
        }

        return responseEntity.getStatusCode();
    }

}
