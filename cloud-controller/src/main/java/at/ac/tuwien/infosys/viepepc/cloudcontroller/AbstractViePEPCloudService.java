package at.ac.tuwien.infosys.viepepc.cloudcontroller;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
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
public abstract class AbstractViePEPCloudService {


    protected void waitUntilVmIsBooted(VirtualMachine virtualMachine) throws VmCouldNotBeStartedException {
        int counter = 0;
        Boolean connection = false;
        do {
            try {
                counter = counter + 1;
                TimeUnit.SECONDS.sleep(1);
                final DockerClient docker = DefaultDockerClient.builder().uri(URI.create("http://" + virtualMachine.getIpAddress() + ":2375")).connectTimeoutMillis(100000).connectionPoolSize(20).build();
                docker.ping();
                connection = true;
            } catch (InterruptedException | DockerException e) {
                log.debug("VM " + virtualMachine + " is not available yet.");
            }
        } while (!connection && counter <= 5);
        if (!connection) {
            throw new VmCouldNotBeStartedException("VM " + virtualMachine + " is not available.");
        }
    }

    public boolean checkAvailabilityOfDockerhost(VirtualMachine vm) {
        final DockerClient docker = DefaultDockerClient.builder().uri("http://" + vm.getIpAddress() + ":2375").connectTimeoutMillis(10000).connectionPoolSize(20).build();
        try {
            return docker.ping().equals("OK");
        } catch (DockerException | InterruptedException e) {
            return false;
        }
    }

}
