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

@Component
@Slf4j
public class VMDeploymentController {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CloudControllerService cloudControllerService;
    @Autowired
    private ReportDaoService reportDaoService;

    @Async
    public String deployVirtualMachine(VirtualMachineInstance virtualMachineInstance) throws VmCouldNotBeStartedException {

        Object waitObject = cacheVirtualMachineService.getVmDeployedWaitObjectMap().get(virtualMachineInstance);
        if (waitObject == null) {
            waitObject = new Object();
            cacheVirtualMachineService.getVmDeployedWaitObjectMap().put(virtualMachineInstance, waitObject);

            try {
                virtualMachineInstance = cloudControllerService.deployVM(virtualMachineInstance);
            } catch (VmCouldNotBeStartedException e) {
                synchronized (waitObject) {
                    waitObject.notifyAll();
                }
                cacheVirtualMachineService.getVmDeployedWaitObjectMap().remove(virtualMachineInstance);
                reset(virtualMachineInstance, "VM could not be started");
                throw e;
            }

            log.info("VM up and running with ip: " + virtualMachineInstance.getIpAddress() + " vm: " + virtualMachineInstance);
            VirtualMachineReportingAction report = new VirtualMachineReportingAction(virtualMachineInstance.getStartTime(), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.START);
            reportDaoService.save(report);

            synchronized (waitObject) {
                waitObject.notifyAll();
            }
            cacheVirtualMachineService.getVmDeployedWaitObjectMap().remove(virtualMachineInstance);
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
                throw new VmCouldNotBeStartedException("VM could not be started");
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

}
