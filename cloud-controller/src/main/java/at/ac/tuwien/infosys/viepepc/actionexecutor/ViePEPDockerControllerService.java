package at.ac.tuwien.infosys.viepepc.actionexecutor;


import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.exceptions.DockerException;

/**
 *
 */
public interface ViePEPDockerControllerService {

    Container startContainer(VirtualMachine virtualMachine, Container container) throws DockerException, InterruptedException;

    Container startContainer(Container container) throws DockerException, InterruptedException;

    void removeContainer(Container container);

}
