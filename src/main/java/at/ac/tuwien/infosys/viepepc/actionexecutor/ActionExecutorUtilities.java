package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.database.entities.Action;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ActionExecutorUtilities {

    @Autowired
    private ViePEPCloudService viePEPCloudService;
    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private ViePEPDockerControllerService containerControllerService;

    public void terminateVM(VirtualMachine virtualMachine) {

        log.info("Terminate: " + virtualMachine);

        virtualMachine.setTerminating(true);

        if (virtualMachine.getDeployedContainers().size() > 0) {
            virtualMachine.getDeployedContainers().forEach(container -> stopContainer(container));
        }

        viePEPCloudService.stopVirtualMachine(virtualMachine);

        virtualMachine.terminate();

        VirtualMachineReportingAction report = new VirtualMachineReportingAction(DateTime.now(), virtualMachine.getInstanceId(), virtualMachine.getVmType().getIdentifier().toString(), Action.STOPPED);
        reportDaoService.save(report);
    }

    public void stopContainer(Container container) {
        VirtualMachine vm = container.getVirtualMachine();
        log.info("Stop Container: " + container + " on VM: " + vm);

        ContainerReportingAction report = new ContainerReportingAction(DateTime.now(), container.getName(), vm.getInstanceId(), Action.STOPPED);
        reportDaoService.save(report);

        containerControllerService.removeContainer(container);

        //container.shutdownContainer();

    }

}
