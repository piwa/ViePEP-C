package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@Component
@Slf4j
public class ViePEPAWSFargateSimulationServiceImpl {

    @Value("${viepep.node.port.available}")
    private String encodedHostNodeAvailablePorts;

    public synchronized Container startContainer(Container container) throws DockerException, InterruptedException {

        String id = UUID.randomUUID().toString();
        String hostPort = "2000";

        container.setContainerID(id);
        container.setRunning(true);
        container.setStartedAt(new DateTime());
        container.setExternPort(hostPort);

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
