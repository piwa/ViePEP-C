package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.containerinstance.ContainerGroup;
import com.microsoft.azure.management.containerinstance.ContainerGroupRestartPolicy;
import com.microsoft.azure.management.containerinstance.Port;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;
import com.microsoft.rest.LogLevel;
import com.microsoft.rest.ServiceCallback;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@Component
@Slf4j
public class AzureContainerServiceImpl {

    @Value("${viepep.node.port.available}")
    private String encodedHostNodeAvailablePorts;
    @Value("${container.deployment.region}")
    private String defaultRegion;

    @Value("${azure.client.id}")
    private String clientId;
    @Value("${azure.tenant.id}")
    private String tenantId;
    @Value("${azure.secret}")
    private String secret;
    @Value("${azure.repository}")
    private String repository;
    @Value("${azure.resource.group}")
    private String resourceGroup;
    @Value("${azure.username}")
    private String username;
    @Value("${azure.password}")
    private String password;

    @Value("${spring.rabbitmq.host}")
    private String rabbitMQHost;

    private Azure azure;

    private Region lastRegion = Region.EUROPE_WEST;

    public void setup() {

        if (azure == null) {
            try {
                ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
                        this.clientId,
                        this.tenantId,
                        this.secret,
                        AzureEnvironment.AZURE);

                azure = Azure.configure()
                        .withLogLevel(LogLevel.NONE)
                        .authenticate(credentials)
                        .withDefaultSubscription();
            } catch (Exception ex) {
                log.error("Exception", ex);
            }
        }

    }

    public Container startContainer(Container container) throws DockerException, InterruptedException {

//        try {

        setup();

//            double cores = container.getContainerConfiguration().getCores();
//            double ram = container.getContainerConfiguration().getRam() / 1000;
        double cores = 2;
        double ram = 2;
        Integer internalPort = container.getContainerImage().getServiceType().getServiceTypeResources().getInternPort();

        String aciName = SdkContext.randomResourceName("viepep-ser-", 24);
        String rgName = this.resourceGroup;

        String containerImageName = container.getContainerImage().getRepoName() + "/" + container.getContainerImage().getImageName() + ":latest";

        Region region;
        if (lastRegion == Region.EUROPE_WEST) {
            region = Region.EUROPE_NORTH;
            rgName = rgName + "-n";
        } else {
            region = Region.EUROPE_WEST;
            rgName = rgName + "-w";
        }
        lastRegion = region;

        StopWatch stopwatch = new StopWatch();
        stopwatch.start();

        ServiceCallback<ContainerGroup> var1 = new ServiceCallback<ContainerGroup>() {
            @Override
            public void failure(Throwable throwable) {
                log.error("Exception", throwable);
            }

            @Override
            public void success(ContainerGroup containerGroup) {
//                    log.debug("Container started");
            }
        };

        while (true) {
            try {
                azure.containerGroups().define(aciName)
                        .withRegion(region)
                        .withExistingResourceGroup(rgName)
                        .withLinux()
                        .withPrivateImageRegistry(this.repository, this.username, this.password)
                        .withoutVolume()
                        .defineContainerInstance(aciName + "-1").withImage(containerImageName)
                        .withExternalTcpPort(internalPort)
                        .withCpuCoreCount(cores)
                        .withMemorySizeInGB(ram)
                        .withEnvironmentVariable("spring_rabbitmq_host", rabbitMQHost)
                        .attach()
                        .withRestartPolicy(ContainerGroupRestartPolicy.NEVER)
//                    .withDnsPrefix(aciName)
                        .createAsync(var1);
                break;
            } catch (Exception ex) {
                log.error("Exception in create routine", ex);
                log.error("I will wait 1 sec and try again", ex);
                TimeUnit.SECONDS.sleep(1);
            }
        }

        while (!container.getContainerStatus().equals(ContainerStatus.DEPLOYED)) {
            try {
                ContainerGroup containerGroup = azure.containerGroups().getByResourceGroup(rgName, aciName);
                if (containerGroup != null && containerGroup.state() != null && containerGroup.state().equals("Running")) {
                    container.setProviderContainerId(containerGroup.id());
                    container.setContainerID(aciName);
                    container.setContainerStatus(ContainerStatus.DEPLOYED);
                    container.setStartDate(new DateTime());
                    for (Port port : containerGroup.externalPorts()) {
                        container.setExternPort(String.valueOf(port.port()));
                    }

                    container.setIpAddress("http://" + containerGroup.ipAddress());
                } else {
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (Exception ex) {
                log.error("Exception in wait routine", ex);
                log.error("I will wait 1 sec and try again", ex);
                TimeUnit.SECONDS.sleep(1);
            }
        }

        stopwatch.stop();
        log.debug("Azure container started: duration=" + stopwatch.getTotalTimeSeconds() + ", container=" + container.toString());

//        } catch (Exception ex) {
//            log.error("Exception", ex);
//        }


        return container;
    }


    public void removeContainer(Container container) {

        setup();

        azure.containerGroups().deleteById(container.getProviderContainerId());

        log.debug("The container: " + container.getContainerID() + " was removed.");


    }

}
