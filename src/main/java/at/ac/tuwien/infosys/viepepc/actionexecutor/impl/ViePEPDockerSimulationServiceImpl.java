package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import com.amazonaws.services.ec2.model.Placement;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import jersey.repackaged.com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@Component
@Slf4j
public class ViePEPDockerSimulationServiceImpl {

    @Value("${viepep.node.port.available}")
    private String encodedHostNodeAvailablePorts;

    @Autowired
    private PlacementHelper placementHelper;

    public synchronized Container startContainer(VirtualMachine virtualMachine, Container container) throws DockerException, InterruptedException {


        TimeUnit.MILLISECONDS.sleep(container.getContainerImage().getDeployTime());


        String id = UUID.randomUUID().toString();
        String hostPort = "2000";

        virtualMachine.getDeployedContainers().add(container);
        container.setContainerID(id);
        container.setVirtualMachine(virtualMachine);
        container.setRunning(true);
        container.setStartedAt(new DateTime());
        container.setExternPort(hostPort);



        /* Update the set of used port on docker host */
        List<String> usedPorts = virtualMachine.getUsedPorts();
        usedPorts.add(hostPort);
        virtualMachine.setUsedPorts(usedPorts);


        log.info("A new container with the ID: " + id + " on the host: " + virtualMachine.getInstanceId() + " has been started.");

        return container;
    }


    public void removeContainer(Container container) {


        // Free monitoring port previously used by the docker container
        List<String> usedPorts = container.getVirtualMachine().getUsedPorts();
        usedPorts.remove(container.getExternPort());
        container.getVirtualMachine().setUsedPorts(usedPorts);

        container.shutdownContainer();

        log.info("The container: " + container.getContainerID() + " on the host: " + container.getVirtualMachine() + " was removed.");

    }



}
