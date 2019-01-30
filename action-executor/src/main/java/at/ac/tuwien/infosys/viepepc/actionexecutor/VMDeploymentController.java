package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.CloudControllerService;
import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.Action;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class VMDeploymentController {

    @Autowired
    private CloudControllerService cloudControllerService;
    @Autowired
    private ReportDaoService reportDaoService;

    private ConcurrentMap<VirtualMachineInstance, Object> vmDeployedWaitObjectMap = new ConcurrentHashMap<>();

    @Value("${vm.simulation.deploy.duration.stddev}")
    private int durationStdDev;

    private DateTime getTime(DateTime time) {
        Random rand = new Random();
        int test = rand.ints(-durationStdDev, durationStdDev).findAny().getAsInt();
        return new DateTime(time.getMillis() + test);
    }

    private DateTime getTime2(DateTime time) {
        Random rand = new Random();
        int test = rand.ints(-durationStdDev/5, durationStdDev/5).findAny().getAsInt();
        return new DateTime(time.getMillis() + test);
    }

    @Async
    public String deploy(VirtualMachineInstance virtualMachineInstance) {

        log.info("Deploy VM=" + virtualMachineInstance);
        virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYING);

        Object waitObject = vmDeployedWaitObjectMap.get(virtualMachineInstance);
        if (waitObject == null) {
            waitObject = new Object();
            vmDeployedWaitObjectMap.put(virtualMachineInstance, waitObject);

            try {
                virtualMachineInstance = cloudControllerService.deployVM(virtualMachineInstance);
            } catch (VmCouldNotBeStartedException e) {
                synchronized (waitObject) {
                    waitObject.notifyAll();
                }
                vmDeployedWaitObjectMap.remove(virtualMachineInstance);
                reset(virtualMachineInstance, "VM could not be started");
            }

            log.debug("VM up and running with ip: " + virtualMachineInstance.getIpAddress() + " vm: " + virtualMachineInstance);
//            VirtualMachineReportingAction report = new VirtualMachineReportingAction(virtualMachineInstance.getStartTime(), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.START);
            VirtualMachineReportingAction report = new VirtualMachineReportingAction(getTime(virtualMachineInstance.getScheduledCloudResourceUsage().getStart()), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.START);
            reportDaoService.save(report);

            synchronized (waitObject) {
                waitObject.notifyAll();
            }
            vmDeployedWaitObjectMap.remove(virtualMachineInstance);
        } else {
            try {
                synchronized (waitObject) {
                    waitObject.wait();
                }
            } catch (InterruptedException e) {
                log.error("Exception", e);
            }
            if (!virtualMachineInstance.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYED)) {
                reset(virtualMachineInstance, "VM could not be started");
//                throw new VmCouldNotBeStartedException("VM could not be started");
            }
        }

        return virtualMachineInstance.getIpAddress();
    }

    private void reset(VirtualMachineInstance virtualMachineInstance, String failureReason) {
//        VirtualMachineReportingAction reportVM = new VirtualMachineReportingAction(DateTime.now(), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.FAILED, failureReason);
        VirtualMachineReportingAction reportVM = new VirtualMachineReportingAction(getTime2(virtualMachineInstance.getScheduledCloudResourceUsage().getEnd()), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.FAILED, failureReason);
        reportDaoService.save(reportVM);

        log.debug("Terminate: " + virtualMachineInstance);

        cloudControllerService.stopVirtualMachine(virtualMachineInstance);
        virtualMachineInstance.terminate();
    }

    public void terminate(VirtualMachineInstance virtualMachineInstance) {
        log.info("Terminate: " + virtualMachineInstance);

        virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.TERMINATED);

        cloudControllerService.stopVirtualMachine(virtualMachineInstance);

        virtualMachineInstance.terminate();

//        VirtualMachineReportingAction report = new VirtualMachineReportingAction(DateTime.now(), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.STOPPED);
        VirtualMachineReportingAction report = new VirtualMachineReportingAction(getTime2(virtualMachineInstance.getScheduledCloudResourceUsage().getEnd()), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.STOPPED);
        reportDaoService.save(report);
    }
}
