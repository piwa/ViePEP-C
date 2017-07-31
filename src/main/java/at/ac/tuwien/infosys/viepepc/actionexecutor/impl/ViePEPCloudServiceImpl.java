package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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
    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    @Value("${simulate}")
    private boolean simulate;


    @Override
    @Retryable(maxAttempts=20, backoff=@Backoff(delay=120000, maxDelay=250000))
    public VirtualMachine startVM(VirtualMachine vm) throws VmCouldNotBeStartedException {

        if (simulate) {
            return viePEPCloudSimulatorClientService.startVM(vm);
        } else {
            if (vm.getLocation().equals("aws")) {
                return viePEPAwsClientService.startVM(vm);
            } else {
                return viePEPOpenStackClientService.startVM(vm);
            }
        }
    }

    @Override
    public boolean stopVirtualMachine(VirtualMachine vm) {

        if (simulate) {
            return viePEPCloudSimulatorClientService.stopVirtualMachine(vm);
        }
        else {
            if (vm.getLocation().equals("aws")) {
                return viePEPAwsClientService.stopVirtualMachine(vm);
            } else {
                return viePEPOpenStackClientService.stopVirtualMachine(vm);
            }
        }
    }

    @Override
    public boolean checkAvailabilityOfDockerhost(VirtualMachine vm) {

        if (simulate) {
            return viePEPCloudSimulatorClientService.checkAvailabilityOfDockerhost(vm);
        }
        else {
            if (vm.getLocation().equals("aws")) {
                return viePEPAwsClientService.checkAvailabilityOfDockerhost(vm);
            } else {
                return viePEPOpenStackClientService.checkAvailabilityOfDockerhost(vm);
            }
        }
    }

    @Override
    public Container startContainer(VirtualMachine virtualMachine, Container container) throws DockerException, InterruptedException {

        if (simulate) {

            Thread.sleep(container.getContainerImage().getDeployTime());

            Container container1 = viePEPDockerSimulationService.startContainer(virtualMachine, container);

            return container1;
        } else {
            return viePEPDockerControllerService.startContainer(virtualMachine, container);
        }
    }

    @Override
    public void removeContainer(Container container) {
        if (simulate) {
            viePEPDockerSimulationService.removeContainer(container);
        } else {
            viePEPDockerControllerService.removeContainer(container);
        }
    }
}
