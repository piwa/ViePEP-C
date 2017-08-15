package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.*;
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

@Slf4j
@Component
public class ViePEPGCloudClientService extends AbstractViePEPCloudService {

    private final long OPERATION_TIMEOUT_MILLIS = 60 * 1000;

    private final String NETWORK_INTERFACE_CONFIG = "ONE_TO_ONE_NAT";
    private final String NETWORK_ACCESS_CONFIG = "External NAT";

    private final String APPLICATION_NAME = "viepep-c";

    private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".store/compute_engine_sample");
    private static FileDataStoreFactory dataStoreFactory;
    private static final List<String> SCOPES = Arrays.asList(ComputeScopes.COMPUTE);

    @Value("${gcloud.default.project.id}")
    private String gcloudProjectId;
    @Value("${gcloud.default.region}")
    private String gcloudDefaultRegion;
    @Value("${gcloud.default.image.prefix}")
    private String gcloudImagePrefix = "https://www.googleapis.com/compute/v1/projects/";
    @Value("${gcloud.default.image.path}")
    private String gcloudImagePath = "debian-cloud/global/images/debian-7-wheezy-v20150710";

    public VirtualMachine startVM(VirtualMachine virtualMachine) {


        try {
            Compute compute = setup();


            Instance instance = startInstance(compute, virtualMachine.getName());


            virtualMachine.setResourcepool("gcloud");
            virtualMachine.setInstanceId(instance.getName());
            virtualMachine.setIpAddress(instance.getNetworkInterfaces().get(0).getNetworkIP());
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
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);

        ClassLoader classLoader = getClass().getClassLoader();

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), new InputStreamReader(new FileInputStream("/Users/philippwaibel/Desktop/client_secret_95903565702-7aem8abe3aqpv5ikjoupqr0vfs8p8ii5.apps.googleusercontent.com.json")));


        // Authenticate using Google Application Default Credentials.
        Credential credential = authorize();

//        if (credential.createScopedRequired()) {
//            List<String> scopes = new ArrayList<>();
//            // Set Google Cloud Storage scope to Full Control.
//            scopes.add(ComputeScopes.DEVSTORAGE_FULL_CONTROL);
//            // Set Google Compute Engine scope to Read-write.
//            scopes.add(ComputeScopes.COMPUTE);
//            credential = credential.createScoped(scopes);
//        }


        // Create Compute Engine object for listing instances.
        return new Compute.Builder(httpTransport, JacksonFactory.getDefaultInstance(), null)
                .setApplicationName(APPLICATION_NAME)
                .setHttpRequestInitializer(credential)
                .build();
    }


    private static Credential authorize() throws Exception {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // initialize client secrets object
        GoogleClientSecrets clientSecrets;
        // load client secrets
        clientSecrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), new InputStreamReader(new FileInputStream("/Users/philippwaibel/Desktop/client_secret_95903565702-7aem8abe3aqpv5ikjoupqr0vfs8p8ii5.apps.googleusercontent.com.json")));
        if (clientSecrets.getDetails().getClientId().startsWith("Enter") || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
            System.out.println("Enter Client ID and Secret from https://code.google.com/apis/console/ into compute-engine-cmdline-sample/src/main/resources/client_secrets.json");
            System.exit(1);
        }
        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JacksonFactory.getDefaultInstance(), clientSecrets, SCOPES).setDataStoreFactory(dataStoreFactory)
                .build();
        // authorize
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }


    private Instance startInstance(Compute compute, String instanceName) throws Exception {

        // Create VM Instance object with the required properties.
        Instance instance = new Instance();
        instance.setName(instanceName);
        instance.setMachineType("https://www.googleapis.com/compute/v1/projects/" + gcloudProjectId + "/zones/" + gcloudDefaultRegion + "/machineTypes/n1-standard-1");

        // Add Network Interface to be used by VM Instance.
        NetworkInterface ifc = new NetworkInterface();
        ifc.setNetwork("https://www.googleapis.com/compute/v1/projects/" + gcloudProjectId + "/global/networks/default");
        List<AccessConfig> configs = new ArrayList<>();
        AccessConfig config = new AccessConfig();
        config.setType(NETWORK_INTERFACE_CONFIG);
        config.setName(NETWORK_ACCESS_CONFIG);
        configs.add(config);
        ifc.setAccessConfigs(configs);
        instance.setNetworkInterfaces(Collections.singletonList(ifc));

        // Add attached Persistent Disk to be used by VM Instance.
        AttachedDisk disk = new AttachedDisk();
        disk.setBoot(true);
        disk.setAutoDelete(true);
        disk.setType("PERSISTENT");
        AttachedDiskInitializeParams params = new AttachedDiskInitializeParams();
        // Assign the Persistent Disk the same name as the VM Instance.
        params.setDiskName(instanceName);
        // Specify the source operating system machine image to be used by the VM Instance.
        params.setSourceImage(gcloudImagePrefix + gcloudImagePath);
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


        String cloudInit = "";
        try {
            cloudInit = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("docker-config/cloud-init.ign"), "UTF-8");
        } catch (IOException e) {
            log.error("Could not load cloud init file");
        }

        // Optional - Add a startup script to be used by the VM Instance.
        Metadata meta = new Metadata();
        Metadata.Items item = new Metadata.Items();
        item.setKey("user-data");
        item.setValue(cloudInit);
        meta.setItems(Collections.singletonList(item));
        instance.setMetadata(meta);

        log.debug(instance.toPrettyString());
        Compute.Instances.Insert insert = compute.instances().insert(gcloudProjectId, gcloudDefaultRegion, instance);
        Operation op = insert.execute();

        log.debug("Waiting for operation completion...");
        Operation.Error error = blockUntilComplete(compute, op, OPERATION_TIMEOUT_MILLIS);
        if (error != null) {
            log.error(error.toPrettyString());
            throw new Exception(error.toPrettyString());
        }

        return instance;
    }



    private boolean deleteInstance(Compute compute, String instanceName) throws Exception {
        Compute.Instances.Delete delete = compute.instances().delete(gcloudProjectId, gcloudDefaultRegion, instanceName);
        Operation op = delete.execute();

        log.debug("Waiting for operation completion...");
        Operation.Error error = blockUntilComplete(compute, op, OPERATION_TIMEOUT_MILLIS);
        if (error != null) {
            log.error(error.toPrettyString());
            throw new Exception(error.toPrettyString());
        }
        return true;
    }

    private Operation.Error blockUntilComplete(Compute compute, Operation operation, long timeout) throws Exception {
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
            log.debug("waiting...");
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
