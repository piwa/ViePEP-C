package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.CloudControllerService;
import at.ac.tuwien.infosys.viepepc.cloudcontroller.DockerControllerService;
import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 21/04/2017.
 */
@Component
@Slf4j
public class CloudServiceImpl implements CloudControllerService, DockerControllerService {

    @Autowired
    private CloudSimulatorClientService cloudSimulatorClientService;
    @Autowired
    private AwsClientService awsClientService;
    @Autowired
    private OpenStackClientService openStackClientService;
    @Autowired
    private DockerControllerServiceImpl viePEPDockerControllerService;
    @Autowired
    private DockerSimulationServiceImpl viePEPDockerSimulationService;
    @Autowired
    private GCloudClientService gCloudClientService;
    @Autowired
    private AWSFargateSimulationServiceImpl viePEPAWSFargateSimulation;
    @Autowired
    private AzureContainerServiceImpl azureContainerServiceImpl;

    @Value("${simulate}")
    private boolean simulate;

    @Value("${only.container.deploy.time}")
    private long onlyContainerDeployTime;

    @Value("${container.imageNotAvailable.simulation.deploy.duration.average}")
    private int imageNotAvailableAverage;
    @Value("${container.imageNotAvailable.simulation.deploy.duration.stddev}")
    private int imageNotAvailableStdDev;
    @Value("${container.imageAvailable.simulation.deploy.duration.average}")
    private int imageAvailableAverage;
    @Value("${container.imageAvailable.simulation.deploy.duration.stddev}")
    private int imageAvailableStdDev;


    @Override
//    @Retryable(maxAttempts=10, backoff=@Backoff(delay=30000, maxDelay=120000, random = true))
    public VirtualMachineInstance deployVM(VirtualMachineInstance vm) throws VmCouldNotBeStartedException {

        if (simulate) {
            return cloudSimulatorClientService.startVM(vm);
        } else if (vm.getVmType().getLocation().equals("aws")) {
            return awsClientService.startVM(vm);
        } else if (vm.getVmType().getLocation().equals("gcloud")) {
            return gCloudClientService.startVM(vm);
        } else {
            return openStackClientService.startVM(vm);
        }

    }

    @Override
    public boolean stopVirtualMachine(VirtualMachineInstance vm) {

        if (simulate) {
            return cloudSimulatorClientService.stopVirtualMachine(vm);
        } else if (vm.getVmType().getLocation().equals("aws")) {
            return awsClientService.stopVirtualMachine(vm);
        } else if (vm.getVmType().getLocation().equals("gcloud")) {
            return gCloudClientService.stopVirtualMachine(vm);
        } else {
            return openStackClientService.stopVirtualMachine(vm);
        }

    }

    @Override
    public boolean checkAvailabilityOfDockerhost(VirtualMachineInstance vm) {

        if (simulate) {
            return cloudSimulatorClientService.checkAvailabilityOfDockerhost(vm);
        } else if (vm.getVmType().getLocation().equals("aws")) {
            return awsClientService.checkAvailabilityOfDockerhost(vm);
        } else if (vm.getVmType().getLocation().equals("gcloud")) {
            return gCloudClientService.checkAvailabilityOfDockerhost(vm);
        } else {
            return openStackClientService.checkAvailabilityOfDockerhost(vm);
        }

    }

    @Override
    public Container startContainer(VirtualMachineInstance virtualMachineInstance, Container container) throws DockerException, InterruptedException {

        container.setContainerStatus(ContainerStatus.DEPLOYING);
        if (simulate) {
            if (!virtualMachineInstance.getAvailableContainerImages().contains(container.getContainerImage())) {
                TimeUnit.MILLISECONDS.sleep(getSleepTime(imageNotAvailableAverage, imageNotAvailableStdDev));
            } else {
                TimeUnit.MILLISECONDS.sleep(getSleepTime(imageAvailableAverage, imageAvailableStdDev));
            }
            container = viePEPDockerSimulationService.startContainer(virtualMachineInstance, container);

        } else {
            container = viePEPDockerControllerService.startContainer(virtualMachineInstance, container);
        }

        container.setContainerStatus(ContainerStatus.DEPLOYED);
        container.setStartDate(new DateTime());

        return container;
    }

    @Override
    public Container startContainer(Container container) throws DockerException, InterruptedException {

        container.setContainerStatus(ContainerStatus.DEPLOYING);
        if (simulate) {

            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(14000, 22001));
            container = viePEPAWSFargateSimulation.startContainer(container);

        } else if (container.isBareMetal()) {
            container = azureContainerServiceImpl.startContainer(container);
        } else {
            container = viePEPDockerControllerService.startContainer(container.getVirtualMachineInstance(), container);
        }
        container.setContainerStatus(ContainerStatus.DEPLOYED);
        container.setStartDate(new DateTime());

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
        if (container.isBareMetal()) {
            if (simulate) {
                viePEPAWSFargateSimulation.removeContainer(container);
            } else {
                azureContainerServiceImpl.removeContainer(container);
            }
        } else {
            if (simulate) {
                viePEPDockerSimulationService.removeContainer(container);
            } else {
                viePEPDockerControllerService.removeContainer(container);
            }
        }
    }
}
