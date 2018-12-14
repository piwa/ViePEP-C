package at.ac.tuwien.infosys.viepepc.cloudcontroller;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;

/**
 * Created by philippwaibel on 21/04/2017.
 */
public interface CloudControllerService {

    VirtualMachine startVM(VirtualMachine vm) throws VmCouldNotBeStartedException;

    boolean stopVirtualMachine(VirtualMachine vm);

    boolean checkAvailabilityOfDockerhost(VirtualMachine vm);
}