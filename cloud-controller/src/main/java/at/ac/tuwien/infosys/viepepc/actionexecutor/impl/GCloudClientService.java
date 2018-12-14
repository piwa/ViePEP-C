package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.WaitForOption;
import com.google.cloud.compute.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GCloudClientService extends AbstractViePEPCloudService {

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

    public VirtualMachine startVM(VirtualMachine virtualMachine) throws VmCouldNotBeStartedException {
        try {
            Compute compute = setup();

            if (virtualMachine == null) {
                virtualMachine = new VirtualMachine();
                virtualMachine.getVmType().setFlavor(gcloudMachineType);
            }

            Instance instance = startInstance(compute, virtualMachine);

            virtualMachine.setResourcepool("gcloud");
            virtualMachine.setInstanceId(instance.getGeneratedId());
            if (gcloudUsePublicIp) {
                virtualMachine.setIpAddress(instance.getNetworkInterfaces().get(0).getAccessConfigurations().get(0).getNatIp());
            } else {
                virtualMachine.setIpAddress(instance.getNetworkInterfaces().get(0).getNetworkIp());
            }

            virtualMachine.setLeased(true);

            log.info("VM with id: " + virtualMachine.getInstanceId() + " and IP " + virtualMachine.getIpAddress() + " was started. Waiting for connection...");

            waitUntilVmIsBooted(virtualMachine);

            virtualMachine.setStarted(true);
            virtualMachine.setStartedAt(DateTime.now());

            log.info("VM connection with id: " + virtualMachine.getInstanceId() + " and IP " + virtualMachine.getIpAddress() + " established.");


            return virtualMachine;


        } catch (Exception e) {
            log.error("Exception while booting VM", e);
            throw new VmCouldNotBeStartedException(e);
        }


    }

    public final boolean stopVirtualMachine(VirtualMachine virtualMachine) {
        try {
            Compute compute = setup();
            deleteInstance(compute, virtualMachine.getGoogleName());
        } catch (Exception e) {
            log.error("Exception while stopping VM", e);
            return false;
        }

        return true;
    }

    private Compute setup() throws Exception {
        return ComputeOptions.newBuilder().setCredentials(ServiceAccountCredentials.fromStream(new FileInputStream(gcloudCredentials))).build().getService();
    }

    public Page<Instance> listAllInstances() throws Exception {
        return listAllInstances(setup());
    }

    public Page<Instance> listAllInstances(Compute compute) {
//        Compute.InstanceFilter instanceFilter = Compute.InstanceFilter.equals(Compute.InstanceField.TAGS,"viepep-eval");
//        Compute.InstanceListOption instanceListOption = Compute.InstanceListOption.filter(instanceFilter);
        Compute.InstanceListOption instanceListOption = Compute.InstanceListOption.pageSize(100);
        Page<Instance> instances = compute.listInstances(gcloudDefaultRegion, instanceListOption);
        return instances;
    }

    private Instance startInstance(Compute compute, VirtualMachine virtualMachine) throws Exception {

        ImageId imageId = ImageId.of(gcloudImageProject, gcloudImageId);
        NetworkId networkId = NetworkId.of("default");
        AttachedDisk attachedDisk = AttachedDisk.newBuilder(AttachedDisk.CreateDiskConfiguration.newBuilder(imageId).setAutoDelete(true).build()).build();

        NetworkInterface networkInterface;
        if (gcloudUsePublicIp) {
            networkInterface = NetworkInterface.newBuilder(networkId).setAccessConfigurations(NetworkInterface.AccessConfig.newBuilder().setName("external-nat").build()).build();
        } else {
            networkInterface = NetworkInterface.newBuilder(networkId).build();
        }
        InstanceId instanceId = InstanceId.of(gcloudDefaultRegion, virtualMachine.getGoogleName());
        MachineTypeId machineTypeId = MachineTypeId.of(gcloudDefaultRegion, virtualMachine.getVmType().getFlavor());

        String cloudInit = "";
        try {
            cloudInit = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("docker-config/cloud-init.ign"), "UTF-8");
        } catch (IOException e) {
            log.error("Could not load cloud init file");
        }

        Metadata metadata = Metadata.newBuilder().add("user-data", cloudInit).build();
        SchedulingOptions schedulingOptions = SchedulingOptions.preemptible();

        InstanceInfo instanceInfo = InstanceInfo.of(instanceId, machineTypeId, attachedDisk, networkInterface);

        if (!gcloudUsePublicIp) {
            Tags tags = Tags.newBuilder().setValues("private-nat").build();
            instanceInfo = instanceInfo.toBuilder().setTags(tags).build();
        }

        instanceInfo = instanceInfo.toBuilder().setMetadata(metadata).setSchedulingOptions(schedulingOptions).build();
        Operation operation = compute.create(instanceInfo);


        Operation completedOperation = operation.waitFor(WaitForOption.checkEvery(10, TimeUnit.SECONDS), WaitForOption.timeout(5, TimeUnit.MINUTES));
        Instance instance = null;
        if (completedOperation == null) {
            // operation no longer exists
            throw new VmCouldNotBeStartedException("Exception during VM booting: operation no longer exists");
        } else if (completedOperation.getErrors() != null) {
            // operation failed, handle error
            StringBuilder builder = new StringBuilder();
            completedOperation.getErrors().forEach(operationError -> builder.append(operationError.getCode()).append(": ").append(operationError.getMessage()).append("\n"));
            throw new VmCouldNotBeStartedException("Exception during VM booting:  operation failed, " + builder.toString());
        } else {
            instance = compute.getInstance(instanceId);
        }

        return instance;
    }


    private boolean deleteInstance(Compute compute, String instanceName) throws Exception {
        InstanceId instanceId = InstanceId.of(gcloudDefaultRegion, instanceName);
        Operation operation = compute.deleteInstance(instanceId);

//        Operation completedOperation = operation.waitFor(WaitForOption.checkEvery(10, TimeUnit.SECONDS), WaitForOption.timeout(5, TimeUnit.MINUTES));

//        if (completedOperation == null) {
//            // operation no longer exists
//            throw new VmCouldNotBeStartedException("Exception during VM deleting: operation no longer exists");
//        } else if (completedOperation.getErrors() != null) {
//            // operation failed, handle error
//            StringBuilder builder = new StringBuilder();
//            completedOperation.getErrors().forEach(operationError -> builder.append(operationError.getCode()).append(": ").append(operationError.getMessage()).append("\n"));
//            throw new VmCouldNotBeStartedException("Exception during VM deleting:  operation failed, " + builder.toString());
//        }

        return true;

    }

}
