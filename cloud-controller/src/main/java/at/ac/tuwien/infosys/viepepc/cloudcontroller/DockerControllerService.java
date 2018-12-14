package at.ac.tuwien.infosys.viepepc.cloudcontroller;


import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.exceptions.DockerException;

/**
 *
 */
public interface DockerControllerService {

    Container startContainer(VirtualMachine virtualMachine, Container container) throws DockerException, InterruptedException;

    Container startContainer(Container container) throws DockerException, InterruptedException;

    void removeContainer(Container container);

}
