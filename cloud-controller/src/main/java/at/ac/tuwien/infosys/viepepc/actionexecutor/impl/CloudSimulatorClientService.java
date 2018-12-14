package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 31/03/2017.
 */
@Slf4j
@Component
public class CloudSimulatorClientService extends AbstractViePEPCloudService {

    @Value("${use.container}")
    private boolean useDocker;

    @Value("${vm.simulation.deploy.duration.average}")
    private int durationAverage;
    @Value("${vm.simulation.deploy.duration.stddev}")
    private int durationStdDev;

    public VirtualMachine startVM(VirtualMachine virtualMachine) {


        try {

            int minDuration = durationAverage - durationStdDev;
            int maxDuration = durationAverage + durationStdDev;
            if (minDuration < 0) {
                minDuration = 0;
            }
            Random rand = new Random();
            int sleepTime = rand.ints(minDuration, maxDuration).findAny().getAsInt();
            TimeUnit.MILLISECONDS.sleep(sleepTime);

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
