package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.internal.util.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.Flavor;
import org.openstack4j.model.compute.FloatingIP;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.ServerCreate;
import org.openstack4j.openstack.OSFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 31/03/2017.
 */
@Slf4j
@Component
public class ViePEPCloudSimulatorClientService extends AbstractViePEPCloudService  {

    @Value("${use.container}")
    private boolean useDocker;

    public VirtualMachine startVM(VirtualMachine virtualMachine) {

        try {
            TimeUnit.MILLISECONDS.sleep(virtualMachine.getStartupTime());
            if (!useDocker) {
                TimeUnit.MILLISECONDS.sleep(virtualMachine.getVmType().getDeployTime());
            }
        } catch (InterruptedException e) {
            log.error("EXCEPTION", e);
        }

        String uri = "128.130.172.211";

        virtualMachine.setResourcepool("simulation");
        virtualMachine.setInstanceId("simulation" + UUID.randomUUID().toString());
        virtualMachine.setIpAddress(uri);
        virtualMachine.setStarted(true);
        virtualMachine.setLeased(true);
        virtualMachine.setStartedAt(DateTime.now());

        log.info("VM with id: " + virtualMachine.getInstanceId() + " and IP " + uri + " was started. Waiting for connection...");

        waitUntilVmIsBooted(virtualMachine);

        log.debug("VM connection with id: " + virtualMachine.getInstanceId() + " and IP " + uri + " established.");


        return virtualMachine;
    }


    public final boolean stopVirtualMachine(VirtualMachine virtualMachine) {
        log.info("VM with id: " + virtualMachine.getInstanceId() + " terminated");
        virtualMachine.setIpAddress(null);
        return true;
    }


    @Override
    public boolean checkAvailabilityOfDockerhost(VirtualMachine vm) {
        return true;

    }

}
