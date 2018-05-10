package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.*;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@Component
@Slf4j
public class ViePEPAWSFargateServiceImpl {

    @Value("${viepep.node.port.available}")
    private String encodedHostNodeAvailablePorts;
    @Value("${container.deployment.region}")
    private String defaultRegion;

    private AmazonECS ecs = AmazonECSClientBuilder.standard().withRegion(defaultRegion).build();

    public synchronized Container startContainer(Container container) throws DockerException, InterruptedException {

        String taskDefinitionArn = "arn:aws:ecs:us-east-1:766062760046:task-definition/viepep-c-service1:1";


        NetworkConfiguration networkConfiguration = new NetworkConfiguration().withAwsvpcConfiguration(
                new AwsVpcConfiguration()
                        .withSubnets("subnet-53ce7419")
                        .withAssignPublicIp(AssignPublicIp.ENABLED) //Crashloops without proper internet NAT set up?
        );

        RunTaskResult runTaskResult = ecs.runTask(new RunTaskRequest()
                .withTaskDefinition(taskDefinitionArn)
                .withLaunchType(LaunchType.FARGATE)
                .withNetworkConfiguration(networkConfiguration)
        );

        DescribeTasksRequest describeTasksRequest = new DescribeTasksRequest();
        describeTasksRequest.withTasks(runTaskResult.getTasks().get(0).getTaskArn());
        for( int i = 0; i <= 20; i++){
            DescribeTasksResult describeTasksResult = ecs.describeTasks(describeTasksRequest);
            continue;
        }
        log.info(runTaskResult.toString());

        container.setAwsTaskArn(runTaskResult.getTasks().get(0).getTaskArn());
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

        StopTaskRequest stopTaskRequest = new StopTaskRequest();
        stopTaskRequest.withTask(container.getAwsTaskArn());
        StopTaskResult stopTaskResult = ecs.stopTask(stopTaskRequest);


        container.shutdownContainer();

        log.info("The container: " + container.getContainerID() + " was removed.");


    }

}
