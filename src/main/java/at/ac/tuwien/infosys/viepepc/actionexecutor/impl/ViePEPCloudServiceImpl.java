package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.TimeUnit;

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
    private ViePEPGCloudClientService viePEPGCloudClientService;

    @Value("${simulate}")
    private boolean simulate;


    @Override
//    @Retryable(maxAttempts=10, backoff=@Backoff(delay=30000, maxDelay=120000, random = true))
    public VirtualMachine startVM(VirtualMachine vm) throws VmCouldNotBeStartedException {

        if (simulate) {
            return viePEPCloudSimulatorClientService.startVM(vm);
        } else if (vm.getLocation().equals("aws")) {
            return viePEPAwsClientService.startVM(vm);
        } else if (vm.getLocation().equals("gcloud")) {
            return viePEPGCloudClientService.startVM(vm);
        } else {
            return viePEPOpenStackClientService.startVM(vm);
        }

    }

    @Override
    public boolean stopVirtualMachine(VirtualMachine vm) {

        if (simulate) {
            return viePEPCloudSimulatorClientService.stopVirtualMachine(vm);
        } else if (vm.getLocation().equals("aws")) {
            return viePEPAwsClientService.stopVirtualMachine(vm);
        } else if (vm.getLocation().equals("gcloud")) {
            return viePEPGCloudClientService.stopVirtualMachine(vm);
        } else {
            return viePEPOpenStackClientService.stopVirtualMachine(vm);
        }

    }

    @Override
    public boolean checkAvailabilityOfDockerhost(VirtualMachine vm) {

        if (simulate) {
            return viePEPCloudSimulatorClientService.checkAvailabilityOfDockerhost(vm);
        } else if (vm.getLocation().equals("aws")) {
            return viePEPAwsClientService.checkAvailabilityOfDockerhost(vm);
        } else if (vm.getLocation().equals("gcloud")) {
            return viePEPGCloudClientService.checkAvailabilityOfDockerhost(vm);
        } else {
            return viePEPOpenStackClientService.checkAvailabilityOfDockerhost(vm);
        }

    }

    @Value("${container.imageNotAvailable.simulation.deploy.duration.average}")
    private int imageNotAvailableAverage;
    @Value("${container.imageNotAvailable.simulation.deploy.duration.stddev}")
    private int imageNotAvailableStdDev;
    @Value("${container.imageAvailable.simulation.deploy.duration.average}")
    private int imageAvailableAverage;
    @Value("${container.imageAvailable.simulation.deploy.duration.stddev}")
    private int imageAvailableStdDev;

    @Override
    public Container startContainer(VirtualMachine virtualMachine, Container container) throws DockerException, InterruptedException {

        if (simulate) {



            if(!virtualMachine.getAvailableContainerImages().contains(container.getContainerImage())) {
                TimeUnit.MILLISECONDS.sleep(getSleepTime(imageNotAvailableAverage, imageNotAvailableStdDev));
            }
            else {
                TimeUnit.MILLISECONDS.sleep(getSleepTime(imageAvailableAverage, imageAvailableStdDev));
            }
            container = viePEPDockerSimulationService.startContainer(virtualMachine, container);

        } else {
            container = viePEPDockerControllerService.startContainer(virtualMachine, container);
        }

        container.setRunning(true);
        container.setStartedAt(new DateTime());

        return container;
    }

    private int getSleepTime(int average, int stdDev) {
        int minDuration = average - stdDev;
        int maxDuration = average + stdDev;
        if (minDuration < 0) {
            minDuration = 0;
        }
        Random rand = new Random();
        return rand.ints(minDuration, maxDuration).findAny().getAsInt();
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
