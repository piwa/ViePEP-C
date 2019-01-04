package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.DockerControllerService;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.library.entities.Action;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ContainerDeploymentController {

    @Autowired
    private DockerControllerService dockerControllerService;
    @Autowired
    private ReportDaoService reportDaoService;

    @Async
    public boolean deployContainer(Container container) {
        VirtualMachineInstance vm = container.getVirtualMachineInstance();

        if (container.getContainerStatus().equals(ContainerStatus.DEPLOYED)) {
            log.info(container + " already running on vm " + container.getVirtualMachineInstance());
            return true;
        }

        try {
            dockerControllerService.startContainer(vm, container);
            ContainerReportingAction report = new ContainerReportingAction(DateTime.now(), container.getName(), container.getContainerConfiguration().getName(), vm.getInstanceId(), Action.START);
            reportDaoService.save(report);
            return true;

        } catch (InterruptedException | DockerException e) {
            log.error("EXCEPTION while deploying Container. Reset execution request.", e);
            reset(container, "EXCEPTION while deploying Container. Reset execution request.");
            return false;
        }
    }

    public void reset(Container container, String failureReason) {
        if (container != null) {
            VirtualMachineInstance vm = container.getVirtualMachineInstance();
            ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), container.getName(), container.getContainerConfiguration().getName(), vm.getInstanceId(), Action.FAILED, failureReason);
            reportDaoService.save(reportContainer);
            container.shutdownContainer();
        }
    }

}
