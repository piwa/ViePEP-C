package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.WaitForOption;
import com.google.cloud.compute.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.internal.util.Base64;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ViePEPGCloudClientService extends AbstractViePEPCloudService {

    private final long OPERATION_TIMEOUT_MILLIS = 60 * 1000;

    private final String NETWORK_INTERFACE_CONFIG = "ONE_TO_ONE_NAT";
    private final String NETWORK_ACCESS_CONFIG = "External NAT";

    private final String APPLICATION_NAME = "viepep-c";

    private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".store/compute_engine_sample");
    private static FileDataStoreFactory dataStoreFactory;
//    private static final List<String> SCOPES = Arrays.asList(ComputeScopes.COMPUTE);

    @Value("${gcloud.default.project.id}")
    private String gcloudProjectId;
    @Value("${gcloud.default.region}")
    private String gcloudDefaultRegion;
    @Value("${gcloud.default.image.project}")
    private String gcloudImageProject;
    @Value("${gcloud.default.image.id}")
    private String gcloudImageId;
    @Value("${gcloud.default.machine.type}")
    private String gcloudMachineType;

    public VirtualMachine startVM(VirtualMachine virtualMachine) {


        try {
            Compute compute = setup();


            Instance instance = startInstance(compute, virtualMachine.getName());


            virtualMachine.setResourcepool("gcloud");
            virtualMachine.setInstanceId(instance.getGeneratedId());//.getInstanceId().getInstance());
            virtualMachine.setIpAddress(instance.getNetworkInterfaces().get(0).getAccessConfigurations().get(0).getNatIp());
            virtualMachine.setStarted(true);
            virtualMachine.setLeased(true);
            virtualMachine.setStartedAt(DateTime.now());
            //size in GB

            log.info("VM with id: " + virtualMachine.getInstanceId() + " and IP " + virtualMachine.getIpAddress() + " was started. Waiting for connection...");

            waitUntilVmIsBooted(virtualMachine);

            log.info("VM connection with id: " + virtualMachine.getInstanceId() + " and IP " + virtualMachine.getIpAddress() + " established.");


            return virtualMachine;


        } catch (Exception e) {
            log.error("Exception", e);
        }

        return null;
    }

    public final boolean stopVirtualMachine(VirtualMachine virtualMachine) {
        try {
            Compute compute = setup();
            deleteInstance(compute, virtualMachine.getName());
        } catch (Exception e) {
            log.error("Exception", e);
            return false;
        }

        return true;
    }

    private Compute setup() throws Exception {
        return ComputeOptions.newBuilder().setCredentials(ServiceAccountCredentials.fromStream(new FileInputStream("/Users/philippwaibel/Desktop/ViePEP-C-959439b30540.json"))).build().getService();
    }



    private Instance startInstance(Compute compute, String instanceName) throws Exception {

        ImageId imageId = ImageId.of(gcloudImageProject, gcloudImageId);
        NetworkId networkId = NetworkId.of("default");
        AttachedDisk attachedDisk = AttachedDisk.of(AttachedDisk.CreateDiskConfiguration.of(imageId));

        NetworkInterface networkInterface = NetworkInterface.newBuilder(networkId).setAccessConfigurations(NetworkInterface.AccessConfig.newBuilder().setName("external-nat").build()).build();
        InstanceId instanceId = InstanceId.of(gcloudDefaultRegion, instanceName);
        MachineTypeId machineTypeId = MachineTypeId.of(gcloudDefaultRegion, gcloudMachineType);

        String cloudInit = "";
        try {
            cloudInit = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("docker-config/cloud-init.ign"), "UTF-8");
        } catch (IOException e) {
            log.error("Could not load cloud init file");
        }
        Metadata metadata = Metadata.newBuilder().add("user-data",cloudInit).build();
        SchedulingOptions schedulingOptions = SchedulingOptions.preemptible();

        InstanceInfo instanceInfo = InstanceInfo.of(instanceId, machineTypeId, attachedDisk, networkInterface);
        instanceInfo = instanceInfo.toBuilder().setMetadata(metadata).setSchedulingOptions(schedulingOptions).build();
        Operation operation = compute.create(instanceInfo);


        Operation completedOperation = operation.waitFor(WaitForOption.checkEvery(10, TimeUnit.SECONDS), WaitForOption.timeout(5, TimeUnit.MINUTES));
        Instance instance = null;
        if (completedOperation == null) {
            // operation no longer exists
        } else if (completedOperation.getErrors() != null) {
            // operation failed, handle error
        } else {
            instance = compute.getInstance(instanceId);
        }


        return instance;
    }



    private boolean deleteInstance(Compute compute, String instanceName) throws Exception {

        InstanceId instanceId = InstanceId.of(gcloudDefaultRegion, instanceName);
        Operation operation = compute.deleteInstance(instanceId);
        Operation completedOperation = operation.waitFor(WaitForOption.checkEvery(10, TimeUnit.SECONDS), WaitForOption.timeout(5, TimeUnit.MINUTES));

        if (completedOperation == null) {
            // operation no longer exists
        } else if (completedOperation.getErrors() != null) {
            // operation failed, handle error
        }

        return true;

    }

}
