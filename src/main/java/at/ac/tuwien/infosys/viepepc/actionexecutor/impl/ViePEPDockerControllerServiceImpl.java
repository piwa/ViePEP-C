package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;
import jersey.repackaged.com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@Component
@Slf4j
public class ViePEPDockerControllerServiceImpl {

    @Value("${viepep.node.port.available}")
    private String encodedHostNodeAvailablePorts;
    @Value("${spring.rabbitmq.host}")
    private String rabbitMQHost;

    @Autowired
    private ViePEPCloudService viePEPCloudService;
    @Autowired
    private DockerPullHelper dockerPullHelper;

    public synchronized Container startContainer(VirtualMachine virtualMachine, Container container) throws DockerException, InterruptedException {

        boolean result = checkAvailabilityOfDockerhostWithRetry(virtualMachine);

        if (result == false) {
            throw new DockerException("Dockerhost not available " + virtualMachine.toString());
        }

        /* Connect to docker server of the host */
        final DockerClient docker = DefaultDockerClient.builder().uri("http://" + virtualMachine.getIpAddress() + ":2375").connectTimeoutMillis(60000).connectionPoolSize(20).build();

        String containerImage = container.getContainerImage().getRepoName() + "/" + container.getContainerImage().getImageName();

        dockerPullHelper.pullContainer(docker, containerImage);
//        docker.pull(containerImage);

        String internalPort = String.valueOf(container.getContainerImage().getServiceType().getServiceTypeResources().getInternPort());

        /* Configure docker container */
        Double vmCores = (double) virtualMachine.getVmType().getCores();
        Double containerCores = container.getContainerConfiguration().getCores();

        long containerMemory = (long) container.getContainerConfiguration().getRam() * 1024 * 1024;
        long cpuShares = 1024 / (long) Math.ceil(vmCores / containerCores);

        /* Bind container port (processingNodeServerPort) to an available host port */
        String hostPort = getAvailablePortOnHost(virtualMachine);
        if (hostPort == null) {
            throw new DockerException("Not available port on host " + virtualMachine.getInstanceId() + " to bind a new container");
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
                .env("spring.rabbitmq.host=" + rabbitMQHost)
                .build();

        /* Start docker container */
        final ContainerCreation creation = docker.createContainer(containerConfig);
        final String id = creation.id();
        docker.startContainer(id);

        /* Save docker container information on repository */

        virtualMachine.getDeployedContainers().add(container);
        container.setContainerID(id);
        container.setVirtualMachine(virtualMachine);
        container.setExternPort(hostPort);



        /* Update the set of used port on docker host */
        List<String> usedPorts = virtualMachine.getUsedPorts();
        usedPorts.add(hostPort);
        virtualMachine.setUsedPorts(usedPorts);

/*
        for(int i = 0; i < 10; i++) {
            ContainerInfo info = docker.inspectContainer(id);

            try {
                TopResults topResults = docker.topContainer(id, "ps_args");
                for(String str : topResults.titles()) {
                    System.out.println(str);
                }

            } catch (Exception ex ) {
                log.error("Exception", ex);
            }

            TimeUnit.SECONDS.sleep(3);
        }
*/

        log.info("A new container with the ID: " + id + " on the host: " + virtualMachine.getInstanceId() + " has been started.");

        return container;
    }

    private boolean checkAvailabilityOfDockerhostWithRetry(VirtualMachine virtualMachine) {

        for (int i = 0; i < 10; i++) {
            if (viePEPCloudService.checkAvailabilityOfDockerhost(virtualMachine)) {
                return true;
            }
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                log.error("Exception", e);
            }
        }

        return false;
    }


    public void removeContainer(Container container) {
        VirtualMachine virtualMachine = container.getVirtualMachine();
        final DockerClient docker = DefaultDockerClient.builder().uri("http://" + virtualMachine.getIpAddress() + ":2375").connectTimeoutMillis(60000).build();


        try {
            int count = 0;
            int maxTries = 5;
            while (true) {
                try {
                    docker.killContainer(container.getContainerID());
                    break;
                } catch (InterruptedException | DockerException e) {
                    log.warn("Could not kill a docker container - trying again. " + container.toString());
                    if (++count == maxTries) throw e;
                }
            }
        } catch (DockerException | InterruptedException e) {
            log.error("Could not kill the container " + container.toString());
        }

        try {
            int count = 0;
            int maxTries = 5;
            while (true) {
                try {
                    docker.removeContainer(container.getContainerID());
                    break;
                } catch (InterruptedException | DockerException e) {
                    log.warn("Could not remove a docker container - trying again. " + container.toString());
                    if (++count == maxTries) throw e;
                }
            }
        } catch (DockerException | InterruptedException e) {
            log.error("Could not remove the container " + container.toString());
        }

        // Free monitoring port previously used by the docker container
        List<String> usedPorts = virtualMachine.getUsedPorts();
        usedPorts.remove(container.getExternPort());
        virtualMachine.setUsedPorts(usedPorts);

        container.shutdownContainer();

        log.info("The container: " + container.getContainerID() + " on the host: " + virtualMachine + " was removed.");

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
