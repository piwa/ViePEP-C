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
import org.springframework.util.StopWatch;

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

    public synchronized Container startContainer(VirtualMachine virtualMachine, Container container) throws DockerException, InterruptedException {

//        StopWatch stopWatch = new StopWatch();

//        stopWatch.start("set container info");
        String id = UUID.randomUUID().toString();
        String hostPort = "2000";

        virtualMachine.getDeployedContainers().add(container);
        virtualMachine.getAvailableContainerImages().add(container.getContainerImage());
        container.setContainerID(id);
        container.setVirtualMachine(virtualMachine);
        container.setRunning(true);
        container.setStartedAt(new DateTime());
        container.setExternPort(hostPort);
//        stopWatch.stop();

//        stopWatch.start("set used ports");
        /* Update the set of used port on docker host */
        virtualMachine.getUsedPorts().add(hostPort);
//        stopWatch.stop();

//        stopWatch.start("text output");
//        log.info("A new container with the ID: " + id + " on the host: " + virtualMachine.getInstanceId() + " has been started.");
//        stopWatch.stop();
//        log.info("Container deploy time: " + container.toString() + "\n" + stopWatch.getTotalTimeMillis());

        return container;
    }


    public void removeContainer(Container container) {


        if(container.getVirtualMachine() != null) {
            // Free monitoring port previously used by the docker container
            List<String> usedPorts = container.getVirtualMachine().getUsedPorts();
            usedPorts.remove(container.getExternPort());
            container.getVirtualMachine().setUsedPorts(usedPorts);
        }

        container.shutdownContainer();

        if(container.getVirtualMachine() != null) {
            log.debug("The container: " + container.getContainerID() + " on the host: " + container.getVirtualMachine() + " was removed.");
        }
        else {
            log.debug("The container: " + container.getContainerID() + " was removed.");
        }

    }



}
