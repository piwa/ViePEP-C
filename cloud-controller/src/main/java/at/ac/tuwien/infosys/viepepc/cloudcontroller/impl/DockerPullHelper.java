package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class DockerPullHelper {

    @Retryable(maxAttempts = 10, backoff = @Backoff(delay = 30000, maxDelay = 120000, random = true))
    public void pullContainer(DockerClient docker, String containerImage) throws DockerException, InterruptedException {
        docker.pull(containerImage);
    }

}
