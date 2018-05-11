package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.containerinstance.ContainerGroup;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.containerinstance.ContainerGroupRestartPolicy;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;
import com.microsoft.rest.LogLevel;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@Component
@Slf4j
public class ViePEPAzureContainerServiceImpl {

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

    private Azure azure;


    public void setup() {

        try {
            ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
                    this.clientId,
                    this.tenantId,
                    this.secret,
                    AzureEnvironment.AZURE);

            azure = Azure.configure()
                    .withLogLevel(LogLevel.BASIC)
                    .authenticate(credentials)
                    .withDefaultSubscription();

        } catch (Exception ex) {
            log.error("Exception", ex);
        }

    }

    public synchronized Container startContainer(Container container) throws DockerException, InterruptedException {

        try {

            setup();

            StopWatch stopwatch = new StopWatch();
            stopwatch.start();

            String aciName = SdkContext.randomResourceName("viepep-service-", 20);
            String rgName = this.resourceGroup;
            String containerImageName = "viepep.azurecr.io/viepep.backend.services.small:latest";

            ContainerGroup containerGroup = azure.containerGroups().define(aciName)
                    .withRegion(Region.EUROPE_WEST)
                    .withExistingResourceGroup(rgName)
                    .withLinux()
                    .withPrivateImageRegistry(this.repository, this.username, this.password)
                    .withoutVolume()
                    .defineContainerInstance(aciName + "-1")
                    .withImage(containerImageName)
                    .withExternalTcpPort(80)
                    .withCpuCoreCount(.5)
                    .withMemorySizeInGB(0.8)
                    .attach()
                    .withRestartPolicy(ContainerGroupRestartPolicy.NEVER)
                    .withDnsPrefix(aciName)
                    .create();


            AzureUtils.print(containerGroup);

            stopwatch.stop();
            log.info("Task status= " + containerGroup.state() + ", Time=" + stopwatch.getTotalTimeSeconds());


            container.setProviderContainerId(containerGroup.id());
//        String id = UUID.randomUUID().toString();
//        String hostPort = "2000";
//
//        container.setContainerID(id);
//        container.setRunning(true);
//        container.setStartedAt(new DateTime());
//        container.setExternPort(hostPort);
        } catch (Exception ex) {
            log.error("Exception", ex);
        }


        return container;
    }


    public void removeContainer(Container container) {

        setup();

        azure.containerGroups().deleteById(container.getProviderContainerId());

        log.info("The container: " + container.getContainerID() + " was removed.");


    }

}
