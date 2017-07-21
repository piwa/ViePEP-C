package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * Created by philippwaibel on 21/04/2017.
 */
@Component
@Slf4j
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
//            StopWatch stopWatch = new StopWatch();

//            log.info("Start sleep for container " + container.toString());
//            stopWatch.start("deploy container");
            Thread.sleep(container.getContainerImage().getDeployTime());
//            stopWatch.stop();
//            log.info("Sleep for container " + container.toString() + " is over after " + stopWatch.getTotalTimeMillis());

//            stopWatch.start();
            Container container1 = viePEPDockerSimulationService.startContainer(virtualMachine, container);
//            stopWatch.stop();
//            log.debug("Container: " + container.toString() + " start done. Time: \n" + stopWatch.prettyPrint());
            return container1;
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
