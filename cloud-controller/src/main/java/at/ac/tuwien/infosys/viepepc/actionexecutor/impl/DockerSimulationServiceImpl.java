package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@Component
@Slf4j
public class DockerSimulationServiceImpl {

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


        if (container.getVirtualMachine() != null) {
            // Free monitoring port previously used by the docker container
            List<String> usedPorts = container.getVirtualMachine().getUsedPorts();
            usedPorts.remove(container.getExternPort());
            container.getVirtualMachine().setUsedPorts(usedPorts);
        }

        container.shutdownContainer();

        if (container.getVirtualMachine() != null) {
            log.debug("The container: " + container.getContainerID() + " on the host: " + container.getVirtualMachine() + " was removed.");
        } else {
            log.debug("The container: " + container.getContainerID() + " was removed.");
        }

    }


}
