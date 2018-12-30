package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.internal.util.Base64;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 31/03/2017.
 */
@Slf4j
@Component
public class AwsClientService extends AbstractViePEPCloudService {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;

    @Value("${aws.access.key.id}")
    private String awsAccessKeyId;
    @Value("${aws.access.key}")
    private String awsAccessKey;
    @Value("${aws.default.image.id}")
    private String awsDefaultImageId;
    @Value("${aws.default.image.flavor}")
    private String awsDefaultImageFlavor;
    @Value("${aws.default.region}")
    private String awsDefaultRegion;
    @Value("${aws.default.securitygroup}")
    private String awsDefaultSecuritygroup;
    @Value("${aws.keypair.name}")
    private String awsKeypairName;

    private AmazonEC2 amazonEC2Client;

    private void setup() {

        BasicAWSCredentials awsCreds = new BasicAWSCredentials(awsAccessKeyId, awsAccessKey);
        amazonEC2Client = AmazonEC2ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .withRegion(awsDefaultRegion)
                .build();

        log.debug("Successfully connected to AWS with user " + awsAccessKeyId);
    }


    public VirtualMachineInstance startVM(VirtualMachineInstance virtualMachineInstance) throws VmCouldNotBeStartedException {

        try {

            setup();

            if (virtualMachineInstance == null) {
                virtualMachineInstance = new VirtualMachineInstance(cacheVirtualMachineService.getDefaultVMType());
                virtualMachineInstance.getVmType().setFlavorName("m1.large");
            }

            String cloudInit = "";
            try {
                cloudInit = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("docker-config/cloud-init"), "UTF-8");
            } catch (IOException e) {
                log.error("Could not load cloud init file");
            }

            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

            runInstancesRequest.withImageId(awsDefaultImageId)
                    .withInstanceType(virtualMachineInstance.getVmType().getFlavorName())
                    .withMinCount(1)
                    .withMaxCount(1)
                    .withUserData(Base64.encodeAsString(cloudInit))
                    .withKeyName(awsKeypairName)
                    .withSecurityGroups(awsDefaultSecuritygroup);

            RunInstancesResult run_response = amazonEC2Client.runInstances(runInstancesRequest);

            String instanceId = run_response.getReservation().getInstances().get(0).getInstanceId();

            Instance instance = getAwsInstance(instanceId);

            virtualMachineInstance.setResourcepool("aws");
            virtualMachineInstance.setInstanceId(instance.getInstanceId());
            virtualMachineInstance.setIpAddress(instance.getPublicIpAddress());
            virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYED);
            virtualMachineInstance.setStartTime(DateTime.now());
            //size in GB

            log.info("VM with id: " + virtualMachineInstance.getInstanceId() + " and IP " + instance.getPublicIpAddress() + " was started. Waiting for connection...");


            waitUntilVmIsBooted(virtualMachineInstance);


            log.info("VM connection with id: " + virtualMachineInstance.getInstanceId() + " and IP " + instance.getPublicIpAddress() + " established.");

        } catch (Exception e) {
            log.error("Exception while booting VM", e);
            throw new VmCouldNotBeStartedException(e);
        }

        return virtualMachineInstance;
    }

    private Instance getAwsInstance(String instanceId) {


        int counter = 0;

        while (counter <= 50) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                log.error("Exception", e);
            }

            DescribeInstancesRequest request = new DescribeInstancesRequest();
            DescribeInstancesResult response = amazonEC2Client.describeInstances(request);
            for (Reservation reservation : response.getReservations()) {
                for (Instance temp : reservation.getInstances()) {
                    if (temp.getInstanceId().equals(instanceId)) {
                        if (temp.getPublicIpAddress() != null && !temp.getPublicIpAddress().isEmpty()) {
                            return temp;
                        }
                        break;
                    }
                }
            }

            counter = counter + 1;
        }

        return null;

    }


    public final boolean stopVirtualMachine(VirtualMachineInstance virtualMachineInstance) {
        boolean success = stopVirtualMachine(virtualMachineInstance.getInstanceId());
        if (success) {
            virtualMachineInstance.setIpAddress(null);
        }

        return success;

    }

    public final boolean stopVirtualMachine(final String id) {
        boolean terminated = false;
        try {

            setup();

            TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest().withInstanceIds(id);
            amazonEC2Client.terminateInstances(terminateRequest);

            int counter = 0;

            while (!terminated && counter <= 20) {
                TimeUnit.SECONDS.sleep(5);
                DescribeInstancesRequest request = new DescribeInstancesRequest();
                DescribeInstancesResult response = amazonEC2Client.describeInstances(request);
                for (Reservation reservation : response.getReservations()) {
                    for (Instance temp : reservation.getInstances()) {
                        if (temp.getInstanceId().equals(id)) {
                            terminated = temp.getState().getName().equals("terminated");
                            break;
                        }
                    }
                }
                counter = counter + 1;
            }
        } catch (Exception ex) {
            log.error("Exception", ex);
        }

        if (terminated) {
            log.info("VM with id: " + id + " terminated");
        } else {
            log.error("VM with id: " + id + " could not be stopped!");
        }
        return terminated;
    }


}
