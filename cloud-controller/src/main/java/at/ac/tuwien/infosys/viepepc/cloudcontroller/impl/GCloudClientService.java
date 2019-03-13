package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class GCloudClientService extends AbstractViePEPCloudService {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;

    String APPLICATION_NAME = "ViePEP-C/2.0";

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
    @Value("${gcloud.credentials}")
    private String gcloudCredentials;
    @Value("${gcloud.use.public.ip}")
    private boolean gcloudUsePublicIp;

    public VirtualMachineInstance startVM(VirtualMachineInstance virtualMachineInstance) throws VmCouldNotBeStartedException {
        try {
            Compute compute = setup();

            if (virtualMachineInstance == null) {
                virtualMachineInstance = new VirtualMachineInstance(cacheVirtualMachineService.getDefaultVMType());
                virtualMachineInstance.getVmType().setFlavorName(gcloudMachineType);
            }

            Instance instance = startInstance(compute, virtualMachineInstance);
            instance = compute.instances().get(gcloudProjectId, gcloudDefaultRegion, instance.getName()).execute();

            virtualMachineInstance.setResourcepool("gcloud");
            virtualMachineInstance.setInstanceId(instance.getId().toString());

            if (gcloudUsePublicIp) {
                virtualMachineInstance.setIpAddress(instance.getNetworkInterfaces().get(0).getAccessConfigs().get(0).getNatIP());
            } else {
                virtualMachineInstance.setIpAddress(instance.getNetworkInterfaces().get(0).getNetworkIP());
            }

            log.info("VM with id: " + virtualMachineInstance.getInstanceId() + " and IP " + virtualMachineInstance.getIpAddress() + " was started. Waiting for connection...");

            waitUntilVmIsBooted(virtualMachineInstance);

            virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYED);
            virtualMachineInstance.setStartTime(DateTime.now());

            log.info("VM connection with id: " + virtualMachineInstance.getInstanceId() + " and IP " + virtualMachineInstance.getIpAddress() + " established.");


            return virtualMachineInstance;


        } catch (Exception e) {
            log.error("Exception while booting VM", e);
            throw new VmCouldNotBeStartedException(e);
        }


    }

    public final boolean stopVirtualMachine(VirtualMachineInstance virtualMachineInstance) {
        try {
            Compute compute = setup();
            deleteInstance(compute, virtualMachineInstance.getGoogleName());
        } catch (Exception e) {
            log.error("Exception while stopping VM", e);
            return false;
        }

        return true;
    }

    private Compute setup() throws Exception {
        // Authenticate using Google Application Default Credentials.
        GoogleCredential credential = GoogleCredential.getApplicationDefault();
        if (credential.createScopedRequired()) {
            List<String> scopes = new ArrayList<>();
            // Set Google Cloud Storage scope to Full Control.
            scopes.add(ComputeScopes.DEVSTORAGE_FULL_CONTROL);
            // Set Google Compute Engine scope to Read-write.
            scopes.add(ComputeScopes.COMPUTE);
            credential = credential.createScoped(scopes);
        }

        // Create Compute Engine object for listing instances.
        return new Compute.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), credential).setApplicationName(APPLICATION_NAME).build();
    }

    private Instance startInstance(Compute compute, VirtualMachineInstance virtualMachineInstance) throws Exception {

        // Create VM Instance object with the required properties.
        Instance instance = new Instance();
        instance.setName(virtualMachineInstance.getGoogleName());
        instance.setMachineType("https://www.googleapis.com/compute/v1/projects/" + gcloudProjectId + "/zones/" + gcloudDefaultRegion + "/machineTypes/" + virtualMachineInstance.getVmType().getFlavorName());

        // Add Network Interface to be used by VM Instance.
        NetworkInterface ifc = new NetworkInterface();
        ifc.setNetwork("https://www.googleapis.com/compute/v1/projects/" + gcloudProjectId + "/global/networks/default");
        List<AccessConfig> configs = new ArrayList<>();
        if (gcloudUsePublicIp) {
            AccessConfig config = new AccessConfig();
            config.setType("ONE_TO_ONE_NAT");
            config.setName("External NAT");
            configs.add(config);
        }
        ifc.setAccessConfigs(configs);
        instance.setNetworkInterfaces(Collections.singletonList(ifc));

        Tags tags = new Tags();
        List<String> tagStrings = new ArrayList<>();
        tagStrings.add("viepep-c-service");
        tags.setItems(tagStrings);
        instance.setTags(tags);

        // Add attached Persistent Disk to be used by VM Instance.
        AttachedDisk disk = new AttachedDisk();
        disk.setBoot(true);
        disk.setAutoDelete(true);
        disk.setType("PERSISTENT");
        AttachedDiskInitializeParams params = new AttachedDiskInitializeParams();
        // Assign the Persistent Disk the same name as the VM Instance.
        params.setDiskName(virtualMachineInstance.getGoogleName());
        // Specify the source operating system machine image to be used by the VM Instance.
        params.setSourceImage("projects/" + gcloudImageProject + "/global/images/" + gcloudImageId);
        // Specify the disk type as Standard Persistent Disk
        params.setDiskType("https://www.googleapis.com/compute/v1/projects/" + gcloudProjectId + "/zones/" + gcloudDefaultRegion + "/diskTypes/pd-standard");
        disk.setInitializeParams(params);
        instance.setDisks(Collections.singletonList(disk));

        // Initialize the service account to be used by the VM Instance and set the API access scopes.
        ServiceAccount account = new ServiceAccount();
        account.setEmail("default");
        List<String> scopes = new ArrayList<>();
        scopes.add("https://www.googleapis.com/auth/devstorage.full_control");
        scopes.add("https://www.googleapis.com/auth/compute");
        account.setScopes(scopes);
        instance.setServiceAccounts(Collections.singletonList(account));

        // Optional - Add a startup script to be used by the VM Instance.
        Metadata meta = new Metadata();
        Metadata.Items item = new Metadata.Items();
        item.setKey("user-data");

        String cloudInit = "";
        try {
            cloudInit = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("docker-config/cloud-init.ign"), "UTF-8");
        } catch (IOException e) {
            log.error("Could not load cloud init file");
        }

        item.setValue(cloudInit);
        meta.setItems(Collections.singletonList(item));
        instance.setMetadata(meta);

        log.debug(instance.toPrettyString());
        Compute.Instances.Insert insert = compute.instances().insert(gcloudProjectId, gcloudDefaultRegion, instance);
        Operation operation = insert.execute();


        log.debug("Waiting for operation completion...");
        Operation.Error error = blockUntilComplete(compute, operation, 60 * 1000);
        if (error == null) {
            log.debug("Success!");
        } else {
            log.error(error.toPrettyString());
        }
        return instance;
    }


    private boolean deleteInstance(Compute compute, String instanceName) throws Exception {
        Compute.Instances.Delete delete = compute.instances().delete(gcloudProjectId, gcloudDefaultRegion, instanceName);
        delete.execute();

        return true;

    }

    /**
     * Wait until {@code operation} is completed.
     *
     * @param compute   the {@code Compute} object
     * @param operation the operation returned by the original request
     * @param timeout   the timeout, in millis
     * @return the error, if any, else {@code null} if there was no error
     * @throws InterruptedException if we timed out waiting for the operation to complete
     * @throws IOException          if we had trouble connecting
     */
    public Operation.Error blockUntilComplete(Compute compute, Operation operation, long timeout) throws Exception {
        long start = System.currentTimeMillis();
        final long pollInterval = 5 * 1000;
        String zone = operation.getZone();  // null for global/regional operations
        if (zone != null) {
            String[] bits = zone.split("/");
            zone = bits[bits.length - 1];
        }
        String status = operation.getStatus();
        String opId = operation.getName();
        while (operation != null && !status.equals("DONE")) {
            Thread.sleep(pollInterval);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= timeout) {
                throw new InterruptedException("Timed out waiting for operation to complete");
            }
            System.out.println("waiting...");
            if (zone != null) {
                Compute.ZoneOperations.Get get = compute.zoneOperations().get(gcloudProjectId, zone, opId);
                operation = get.execute();
            } else {
                Compute.GlobalOperations.Get get = compute.globalOperations().get(gcloudProjectId, opId);
                operation = get.execute();
            }
            if (operation != null) {
                status = operation.getStatus();
            }
        }
        return operation == null ? null : operation.getError();
    }

}
