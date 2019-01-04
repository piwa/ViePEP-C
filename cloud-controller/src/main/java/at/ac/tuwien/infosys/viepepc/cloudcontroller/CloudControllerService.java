package at.ac.tuwien.infosys.viepepc.cloudcontroller;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;

/**
 * Created by philippwaibel on 21/04/2017.
 */
public interface CloudControllerService {

    VirtualMachineInstance deployVM(VirtualMachineInstance vm) throws VmCouldNotBeStartedException;

    boolean stopVirtualMachine(VirtualMachineInstance vm);

    boolean checkAvailabilityOfDockerhost(VirtualMachineInstance vm);
}