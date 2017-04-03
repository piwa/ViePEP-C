package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import jersey.repackaged.com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@Component
@Slf4j
public class ViePEPDockerControllerServiceImpl {

    @Value("${viepep.node.port.available}")
    private String encodedHostNodeAvailablePorts;

    public synchronized Container startContainer(VirtualMachine virtualMachine, Container container) throws DockerException, InterruptedException {
        /* Connect to docker server of the host */
        final DockerClient docker = DefaultDockerClient.builder().uri("http://" + virtualMachine.getIpAddress() + ":2375").connectTimeoutMillis(60000).build();

        String containerImage = container.getContainerImage().getRepoName() + "/" + container.getContainerImage().getImageName();

        docker.pull(containerImage);

        String internalPort = String.valueOf(container.getContainerImage().getServiceType().getInternPort());

        /* Configure docker container */
        Double vmCores = (double)virtualMachine.getVmType().getCores();
        Double containerCores = container.getContainerConfiguration().getCores();

        long containerMemory = (long) container.getContainerConfiguration().getRam() * 1024 * 1024;
        long cpuShares = 1024 / (long) Math.ceil(vmCores / containerCores);

        /* Bind container port (processingNodeServerPort) to an available host port */
        String hostPort = getAvailablePortOnHost(virtualMachine);
        if (hostPort == null) {
            throw new DockerException("Not available port on host " + virtualMachine.getName() + " to bind a new container");
        }

        final Map<String, List<PortBinding>> portBindings = new HashMap<>();
        portBindings.put(internalPort, Lists.newArrayList(PortBinding.of("0.0.0.0", hostPort)));

        final HostConfig hostConfig = HostConfig.builder()
                .cpuShares(cpuShares)
                .memoryReservation(containerMemory)
                .portBindings(portBindings)
                .networkMode("bridge")
                .build();

        final ContainerConfig containerConfig = ContainerConfig.builder()
                .hostConfig(hostConfig)
                .image(containerImage)
                .exposedPorts(internalPort)
//                .cmd("sh", "-c", "java -jar vispProcessingNode-0.0.1.jar -Djava.security.egd=file:/dev/./urandom")
//                .env(environmentVariables)
                .build();

        /* Start docker container */
        final ContainerCreation creation = docker.createContainer(containerConfig);
        final String id = creation.id();
        docker.startContainer(id);

        /* Save docker container information on repository */

        container.setContainerID(id);
        container.setVirtualMachine(virtualMachine);
        container.setRunning(true);
        container.setStartedAt(new Date());
        container.setExternPort(hostPort);


        /* Update the set of used port on docker host */
        List<String> usedPorts = virtualMachine.getUsedPorts();
        usedPorts.add(hostPort);
        virtualMachine.setUsedPorts(usedPorts);

        log.info("A new container with the ID: " + id + " on the host: " + virtualMachine.getName() + " has been started.");

        return container;
    }


    public void removeContainer(Container container) {
        VirtualMachine virtualMachine = container.getVirtualMachine();
        final DockerClient docker = DefaultDockerClient.builder().uri("http://" + virtualMachine.getIpAddress() + ":2375").connectTimeoutMillis(60000).build();


        try {
            int count = 0;
            int maxTries = 5;
            while(true) {
                try {
                    docker.killContainer(container.getContainerID());
                    break;
                } catch (InterruptedException | DockerException e) {
                    log.warn("Could not kill a docker container - trying again.", e);
                    if (++count == maxTries) throw e;
                }
            }        } catch (DockerException | InterruptedException e) {
            log.error("Could not kill the container", e);
        }

        try {
            int count = 0;
            int maxTries = 5;
            while(true) {
                try {
                    docker.removeContainer(container.getContainerID());
                    break;
                } catch (InterruptedException | DockerException e) {
                    log.warn("Could not remove a docker container - trying again.", e);
                    if (++count == maxTries) throw e;
                }
            }
        } catch (DockerException | InterruptedException e) {
            log.error("Could not remove the container", e);
        }

        // Free monitoring port previously used by the docker container
        List<String> usedPorts = virtualMachine.getUsedPorts();
        usedPorts.remove(container.getExternPort());
        virtualMachine.setUsedPorts(usedPorts);

        container.setRunning(false);

        log.info("The container: " + container.getContainerID() + " on the host: " + container.getVirtualMachine() + " was removed.");

    }


    private String getAvailablePortOnHost(VirtualMachine host) {

        String[] range = encodedHostNodeAvailablePorts.replaceAll("[a-zA-Z\']", "").split("-");
        int poolStart = Integer.valueOf(range[0]);
        int poolEnd = Integer.valueOf(range[1]);

        List<String> usedPorts = host.getUsedPorts();

        for (int port = poolStart; port < poolEnd; port++) {

            String portStr = Integer.toString(port);

            if (!usedPorts.contains(portStr)) {
                return portStr;
            }
        }
        return null;
    }

}
