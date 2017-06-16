package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.exceptions.DockerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 21/04/2017.
 */
@Component
public class ViePEPCloudServiceImpl implements ViePEPCloudService, ViePEPDockerControllerService {

    @Autowired
    private ViePEPCloudSimulatorClientService viePEPCloudSimulatorClientService;
    @Autowired
    private ViePEPAwsClientService viePEPAwsClientService;
    @Autowired
    private ViePEPOpenStackClientService viePEPOpenStackClientService;
    @Autowired
    private ViePEPDockerControllerServiceImpl viePEPDockerControllerService;
    @Autowired
    private ViePEPDockerSimulationServiceImpl viePEPDockerSimulationService;

    @Value("${simulate}")
    private boolean simulate;


    @Override
    public VirtualMachine startVM(VirtualMachine vm) {

        if(simulate) {
            return viePEPCloudSimulatorClientService.startVM(vm);
        }

        if(vm.getLocation().equals("aws")) {
            return viePEPAwsClientService.startVM(vm);
        }
        else {
            return viePEPOpenStackClientService.startVM(vm);
        }

    }

    @Override
    public boolean stopVirtualMachine(VirtualMachine vm) {

        if(simulate) {
            return viePEPCloudSimulatorClientService.stopVirtualMachine(vm);
        }

        if(vm.getLocation().equals("aws")) {
            return viePEPAwsClientService.stopVirtualMachine(vm);
        }
        else {
            return viePEPOpenStackClientService.stopVirtualMachine(vm);
        }
    }

    @Override
    public boolean checkAvailabilityOfDockerhost(VirtualMachine vm) {

        if(simulate) {
            return viePEPCloudSimulatorClientService.checkAvailabilityOfDockerhost(vm);
        }

        if(vm.getLocation().equals("aws")) {
            return viePEPAwsClientService.checkAvailabilityOfDockerhost(vm);
        }
        else {
            return viePEPOpenStackClientService.checkAvailabilityOfDockerhost(vm);
        }
    }

    @Override
    public Container startContainer(VirtualMachine virtualMachine, Container container) throws DockerException, InterruptedException {

        if(simulate) {
            return viePEPDockerSimulationService.startContainer(virtualMachine, container);
        }
        else {
            return viePEPDockerControllerService.startContainer(virtualMachine, container);
        }
    }

    @Override
    public void removeContainer(Container container) {
        if(simulate) {
            viePEPDockerSimulationService.removeContainer(container);
        }
        else {
            viePEPDockerControllerService.removeContainer(container);
        }
    }
}
