package at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.AsyncRestTemplate;

@Component
@Scope("prototype")
@Slf4j
public class ServiceInvokerHttpClient {

    @Retryable(value = Exception.class, maxAttempts = 30, backoff = @Backoff(delay = 1000, maxDelay = 5000))
    public HttpStatus retryHttpGet(String url) throws Exception {

        log.debug("Send " + url);
        AsyncRestTemplate restTemplate = new AsyncRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ListenableFuture<ResponseEntity<String>> responseEntityFuture = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        ResponseEntity<String> responseEntity = responseEntityFuture.get();

        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new Exception("Exception while sending GET: " + url + "; Status Code: " + responseEntity.getStatusCodeValue());
        }

        return responseEntity.getStatusCode();
    }

}
