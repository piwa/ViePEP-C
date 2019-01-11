package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.AbstractViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
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

    public VirtualMachineInstance startVM(VirtualMachineInstance virtualMachineInstance) {


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

        virtualMachineInstance.setResourcepool("simulation");
        virtualMachineInstance.setInstanceId("simulation" + UUID.randomUUID().toString());
        virtualMachineInstance.setIpAddress(uri);
        virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYED);
        virtualMachineInstance.setStartTime(DateTime.now());

//        log.info("VM with id: " + virtualMachineInstance.getInstanceId() + " and IP " + uri + " was started. Waiting for connection...");
//        log.debug("VM connection with id: " + virtualMachineInstance.getInstanceId() + " and IP " + uri + " established.");


        return virtualMachineInstance;
    }


    public final boolean stopVirtualMachine(VirtualMachineInstance virtualMachineInstance) {
        log.info("VM with id: " + virtualMachineInstance.getInstanceId() + " terminated");
        virtualMachineInstance.setIpAddress(null);
        return true;
    }


    @Override
    public boolean checkAvailabilityOfDockerhost(VirtualMachineInstance vm) {
        return true;

    }

}
