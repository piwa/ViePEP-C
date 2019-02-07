package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@Component
@Slf4j
public class AWSFargateSimulationServiceImpl {

    @Value("${viepep.node.port.available}")
    private String encodedHostNodeAvailablePorts;

    public synchronized Container startContainer(Container container) throws DockerException, InterruptedException {

        String id = UUID.randomUUID().toString();
        String hostPort = "2000";

        container.setContainerID(id);
        container.setContainerStatus(ContainerStatus.DEPLOYED);
        container.setStartDate(new DateTime());
        container.setExternPort(hostPort);

        return container;
    }


    public void removeContainer(Container container) {

        container.shutdownContainer();

        log.debug("The container: " + container.getInternId() + " was removed.");


    }


}
