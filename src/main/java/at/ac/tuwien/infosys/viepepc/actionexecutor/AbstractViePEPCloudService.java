package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 20/04/2017.
 */
@Slf4j
public class AbstractViePEPCloudService {

    protected void waitUntilVmIsBooted(VirtualMachine virtualMachine) {
        Boolean connection = false;
        while (!connection) {
            try {
                TimeUnit.SECONDS.sleep(1);
                final DockerClient docker = DefaultDockerClient.builder().uri(URI.create("http://" + virtualMachine.getIpAddress() + ":2375")).connectTimeoutMillis(100000).build();
                docker.ping();
                connection = true;
            } catch (InterruptedException | DockerException e) {
                log.debug("VM is not available yet.", e);
            }
        }
    }

    public boolean checkAvailabilityOfDockerhost(VirtualMachine vm) {
        final DockerClient docker = DefaultDockerClient.builder().uri("http://" + vm.getIpAddress() + ":2375").connectTimeoutMillis(5000).build();
        try {
            return docker.ping().equals("OK");
        } catch (DockerException | InterruptedException e) {
            return false;
        }
    }

}
