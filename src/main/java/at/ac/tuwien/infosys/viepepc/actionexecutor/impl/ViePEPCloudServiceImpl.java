package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 21/04/2017.
 */
@Component
public class ViePEPCloudServiceImpl implements ViePEPCloudService {

    @Autowired
    private ViePEPAwsClientService viePEPAwsClientService;
    @Autowired
    private ViePEPOpenStackClientService viePEPOpenStackClientService;


    @Override
    public VirtualMachine startVM(VirtualMachine vm) {

        if(vm.getLocation().equals("aws")) {
            return viePEPAwsClientService.startVM(vm);
        }
        else {
            return viePEPOpenStackClientService.startVM(vm);
        }

    }

    @Override
    public boolean stopVirtualMachine(VirtualMachine vm) {
        if(vm.getLocation().equals("aws")) {
            return viePEPAwsClientService.stopVirtualMachine(vm);
        }
        else {
            return viePEPOpenStackClientService.stopVirtualMachine(vm);
        }
    }

    @Override
    public boolean checkAvailabilityOfDockerhost(VirtualMachine vm) {
        if(vm.getLocation().equals("aws")) {
            return viePEPAwsClientService.checkAvailabilityOfDockerhost(vm);
        }
        else {
            return viePEPOpenStackClientService.checkAvailabilityOfDockerhost(vm);
        }
    }
}
