package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.*;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@Component
@Slf4j
public class AWSFargateServiceImpl {

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

    private AmazonECS ecs; //= AmazonECSClientBuilder.standard().withRegion(awsDefaultRegion).build();

    private void init() {

    }

    private void setup() {

        BasicAWSCredentials awsCreds = new BasicAWSCredentials(awsAccessKeyId, awsAccessKey);
        ecs = AmazonECSClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .withRegion(awsDefaultRegion)
                .build();

        log.debug("Successfully connected to AWS with user " + awsAccessKeyId);
    }


    public synchronized Container startContainer(Container container) throws DockerException, InterruptedException {

        String taskDefinitionArn = "arn:aws:ecs:us-east-1:766062760046:task-definition/viepep-c-service1:1";

        setup();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        NetworkConfiguration networkConfiguration = new NetworkConfiguration().withAwsvpcConfiguration(
                new AwsVpcConfiguration()
                        .withSubnets("subnet-d7d023f9")
                        .withAssignPublicIp(AssignPublicIp.ENABLED) //Crashloops without proper internet NAT set up?
        );

        RunTaskResult runTaskResult = ecs.runTask(new RunTaskRequest()
                .withTaskDefinition(taskDefinitionArn)
                .withLaunchType(LaunchType.FARGATE)
                .withNetworkConfiguration(networkConfiguration)
        );


        DescribeTasksRequest describeTasksRequest = new DescribeTasksRequest();
        describeTasksRequest.withTasks(runTaskResult.getTasks().get(0).getTaskArn());
        boolean running = false;
        while (!running) {
            DescribeTasksResult describeTasksResult = ecs.describeTasks(describeTasksRequest);
            running = describeTasksResult.getTasks().get(0).getLastStatus().equals("RUNNING");
            TimeUnit.SECONDS.sleep(2);
        }

        stopWatch.stop();
        log.info("Task running. Time=" + stopWatch.getTotalTimeSeconds());

        container.setContainerID(runTaskResult.getTasks().get(0).getTaskArn());
//        String id = UUID.randomUUID().toString();
//        String hostPort = "2000";
//
//        container.setContainerID(id);
//        container.setRunning(true);
//        container.setStartedAt(new DateTime());
//        container.setExternPort(hostPort);

        return container;
    }


    public void removeContainer(Container container) {

        setup();

        StopTaskRequest stopTaskRequest = new StopTaskRequest();
        stopTaskRequest.withTask(container.getContainerID());
        StopTaskResult stopTaskResult = ecs.stopTask(stopTaskRequest);


        container.shutdownContainer();

        log.debug("The container: " + container.getContainerID() + " was removed.");


    }

}
