package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        stopWatch.stop();

        if(!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new Exception("Exception while sending GET: " + url + "; Status Code: " + responseEntity.getStatusCodeValue());
        }

        return responseEntity.getStatusCode();
    }

}
