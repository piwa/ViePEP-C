package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
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

    public synchronized Container startContainer(VirtualMachineInstance virtualMachineInstance, Container container) throws DockerException, InterruptedException {

//        StopWatch stopWatch = new StopWatch();

//        stopWatch.start("set container info");
        String id = UUID.randomUUID().toString();
        String hostPort = "2000";

        virtualMachineInstance.getDeployedContainers().add(container);
        virtualMachineInstance.getAvailableContainerImages().add(container.getContainerImage());
        container.setContainerID(id);
        container.setVirtualMachineInstance(virtualMachineInstance);
        container.setContainerStatus(ContainerStatus.DEPLOYED);
        container.setStartDate(new DateTime());
        container.setExternPort(hostPort);
//        stopWatch.stop();

//        stopWatch.start("set used ports");
        /* Update the set of used port on docker host */
        virtualMachineInstance.getUsedPorts().add(hostPort);
//        stopWatch.stop();

//        stopWatch.start("text output");
//        log.info("A new container with the ID: " + id + " on the host: " + virtualMachineInstance.getInstanceId() + " has been started.");
//        stopWatch.stop();
//        log.info("Container deploy time: " + container.toString() + "\n" + stopWatch.getTotalTimeMillis());

        return container;
    }


    public void removeContainer(Container container) {


        if (container.getVirtualMachineInstance() != null) {
            // Free monitoring port previously used by the docker container
            List<String> usedPorts = container.getVirtualMachineInstance().getUsedPorts();
            usedPorts.remove(container.getExternPort());
            container.getVirtualMachineInstance().setUsedPorts(usedPorts);
        }

        container.shutdownContainer();

        if (container.getVirtualMachineInstance() != null) {
            log.debug("The container: " + container.getInternId() + " on the host: " + container.getVirtualMachineInstance() + " was removed.");
        } else {
            log.debug("The container: " + container.getInternId() + " was removed.");
        }

    }


}
