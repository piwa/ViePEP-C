package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;

/**
 * Created by philippwaibel on 21/04/2017.
 */
public interface ViePEPCloudService {

    VirtualMachine startVM(VirtualMachine vm);

    boolean stopVirtualMachine(VirtualMachine vm);

    boolean checkAvailabilityOfDockerhost(VirtualMachine vm);
}