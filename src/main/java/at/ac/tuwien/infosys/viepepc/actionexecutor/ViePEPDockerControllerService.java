package at.ac.tuwien.infosys.viepepc.actionexecutor;


import at.ac.tuwien.infosys.viepepc.actionexecutor.impl.exceptions.*;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.messages.ContainerInfo;

import java.util.List;
import java.util.Map;

/**

 */
public interface ViePEPDockerControllerService {

    void initialize();

    /**
     * @param running specified if only currently running container or all should be returned
     * @return a map of VMs and list of Docker containers
     */
    public Map<VirtualMachine, List<Container>> getContainersPerVM(boolean running);

    List<Container> getDockers(VirtualMachine virtualMachine) throws CouldNotGetContainerException;

    Container startDocker(VirtualMachine virtualMachine, Container container) throws CouldNotStartContainerException;

    boolean stopDocker(VirtualMachine virtualMachine, Container container) throws CouldNotStopContainerException;

    ContainerInfo getDockerInfo(VirtualMachine virtualMachine, Container container) throws ContainerNotFoundException;

    Container resizeContainer(VirtualMachine virtualMachine, Container newContainer) throws CouldResizeContainerException;
}
