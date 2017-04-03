package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;

/**
 * Created by philippwaibel on 31/03/2017.
 */
public interface ViePEPOpenStackClientService {
    VirtualMachine startVM(VirtualMachine dh);

    boolean stopVirtualMachine(VirtualMachine dh);

    boolean checkAvailabilityofDockerhost(String url);
}
