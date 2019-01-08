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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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

    @Async
    public String deploy(VirtualMachineInstance virtualMachineInstance) {

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

            log.info("VM up and running with ip: " + virtualMachineInstance.getIpAddress() + " vm: " + virtualMachineInstance);
            VirtualMachineReportingAction report = new VirtualMachineReportingAction(virtualMachineInstance.getStartTime(), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.START);
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
        VirtualMachineReportingAction reportVM = new VirtualMachineReportingAction(DateTime.now(), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.FAILED, failureReason);
        reportDaoService.save(reportVM);

        log.info("Terminate: " + virtualMachineInstance);

        cloudControllerService.stopVirtualMachine(virtualMachineInstance);
        virtualMachineInstance.terminate();
    }

    public void terminate(VirtualMachineInstance virtualMachineInstance) {
        log.info("Terminate: " + virtualMachineInstance);

        virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.TERMINATED);

//        if (virtualMachineInstance.getDeployedContainers().size() > 0) {
//            virtualMachineInstance.getDeployedContainers().forEach(container -> stopContainer(container));
//        }

        cloudControllerService.stopVirtualMachine(virtualMachineInstance);

        virtualMachineInstance.terminate();

        VirtualMachineReportingAction report = new VirtualMachineReportingAction(DateTime.now(), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.STOPPED);
        reportDaoService.save(report);
    }
}
