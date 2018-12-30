package at.ac.tuwien.infosys.viepepc.cloudcontroller;

import at.ac.tuwien.infosys.viepepc.library.entities.Action;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ActionExecutorUtilities {

    @Autowired
    private CloudControllerService cloudControllerService;
    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private DockerControllerService containerControllerService;

    public void terminateVM(VirtualMachineInstance virtualMachineInstance) {

        log.info("Terminate: " + virtualMachineInstance);

        virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.TERMINATED);

        if (virtualMachineInstance.getDeployedContainers().size() > 0) {
            virtualMachineInstance.getDeployedContainers().forEach(container -> stopContainer(container));
        }

        cloudControllerService.stopVirtualMachine(virtualMachineInstance);

        virtualMachineInstance.terminate();

        VirtualMachineReportingAction report = new VirtualMachineReportingAction(DateTime.now(), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.STOPPED);
        reportDaoService.save(report);
    }

    public void stopContainer(Container container) {

        synchronized (container) {

            if (container.getContainerStatus().equals(ContainerStatus.DEPLOYED)) {

                if (container.getVirtualMachineInstance() != null) {
                    VirtualMachineInstance vm = container.getVirtualMachineInstance();
                    log.info("Stop Container: " + container + " on VM: " + vm);

                    ContainerReportingAction report = new ContainerReportingAction(DateTime.now(), container.getName(), container.getContainerConfiguration().getName(), vm.getInstanceId(), Action.STOPPED);
                    reportDaoService.save(report);

                    containerControllerService.removeContainer(container);
                } else {
                    log.info("Stop Container: " + container);

                    ContainerReportingAction report = new ContainerReportingAction(DateTime.now(), container.getName(), container.getContainerConfiguration().getName(), null, Action.STOPPED);
                    reportDaoService.save(report);

                    containerControllerService.removeContainer(container);
                }
            }
        }
    }

}
