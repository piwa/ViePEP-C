package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPClientService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPDockerControllerService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPOpenStackClientService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.impl.exceptions.CouldNotStartContainerException;
import at.ac.tuwien.infosys.viepepc.database.entities.Action;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Component
@Scope("prototype")
@Slf4j
public class LeaseVMAndStartExecution {

    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private ViePEPOpenStackClientService viePEPClientService;
    @Autowired
    private ServiceExecution serviceExecution;
    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private ViePEPDockerControllerService dockerControllerService;

    @Value("${simulate}")
    private boolean simulate;
    @Value("${use.container}")
    private boolean useDocker;


    @Async
    public void leaseVMAndStartExecutionOnVirtualMachine(VirtualMachine virtualMachine, List<ProcessStep> processSteps) {

        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String address = startVM(virtualMachine);

        VirtualMachineReportingAction report = new VirtualMachineReportingAction(new Date(), virtualMachine.getName(), Action.START);
        reportDaoService.save(report);

        if (address == null) {
            log.error("VM " + virtualMachine.getName() + " was not started, reset task");
            for (ProcessStep processStep : processSteps) {
                processStep.setStartDate(null);
                processStep.setScheduled(false);
                processStep.setScheduledAtVM(null);
            }
            return;
        } else {
            long time = stopWatch.getTotalTimeMillis();
            stopWatch.stop();
            virtualMachine.setStartupTime(time);
            virtualMachine.setStarted(true);
            virtualMachine.setIpAddress(address);

            startExecutionsOnVirtualMachine(processSteps, virtualMachine);
        }
    }

    @Async
    public void leaseVMAndStartExecutionOnContainer(VirtualMachine virtualMachine, Map<Container, List<ProcessStep>> containerProcessSteps) {
        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String address = startVM(virtualMachine);

        VirtualMachineReportingAction report = new VirtualMachineReportingAction(new Date(), virtualMachine.getName(), Action.START);
        reportDaoService.save(report);

        if (address == null) {
            log.error("VM " + virtualMachine.getName() + " was not started, reset task");
            for (Container container : containerProcessSteps.keySet()) {
                for (ProcessStep processStep : containerProcessSteps.get(container)) {
                    processStep.setStartDate(null);
                    processStep.setScheduled(false);
                    processStep.setScheduledAtVM(null);
                }
                container.shutdownContainer();
            }
            return;
        } else {
            long time = stopWatch.getTotalTimeMillis();
            stopWatch.stop();
            virtualMachine.setStartupTime(time);
            virtualMachine.setStarted(true);
            virtualMachine.setIpAddress(address);

            startExecutionsOnContainer(containerProcessSteps, virtualMachine);

        }
    }

    public void startExecutionsOnVirtualMachine(final List<ProcessStep> processSteps, final VirtualMachine virtualMachine) {
        for (final ProcessStep processStep : processSteps) {
            processStep.setStartDate(DateTime.now());
            serviceExecution.startExecution(processStep, virtualMachine);

        }
    }

    public void startExecutionsOnContainer(Map<Container, List<ProcessStep>> containerProcessSteps, VirtualMachine virtualMachine) {
        for (Map.Entry<Container, List<ProcessStep>> entry : containerProcessSteps.entrySet()) {
            deployContainer(virtualMachine, entry.getKey());
            for (final ProcessStep processStep : entry.getValue()) {
                processStep.setStartDate(DateTime.now());
                serviceExecution.startExecution(processStep, entry.getKey());
            }
        }
    }

    private String startVM(VirtualMachine virtualMachine) {
        try {
            if (simulate) {
                virtualMachine.setIpAddress("128.130.172.211");

                TimeUnit.MILLISECONDS.sleep(virtualMachine.getStartupTime());
                /* if we are not in Docker mode, additionally sleep some time for deployment of the service */
                if (!useDocker) {
                    TimeUnit.MILLISECONDS.sleep(virtualMachine.getVmType().getDeployTime());
                }

            } else {
                virtualMachine = viePEPClientService.startVM(virtualMachine);
                log.info("VM up and running with ip: " + virtualMachine.getIpAddress() + " vm: " + virtualMachine);

                TimeUnit.MILLISECONDS.sleep(virtualMachine.getVmType().getDeployTime());
            }
        } catch (InterruptedException e) {
            log.error("EXCEPTION while starting VM", e);
        }
        return virtualMachine.getIpAddress();
    }

    private void deployContainer(VirtualMachine vm, Container container) {
        if (container.isRunning()) {
            log.info("Container " + container + " already running on vm " + container.getVirtualMachine());
            return;
        }

        try {
            if (simulate) {
                if (placementHelper.imageForContainerEverDeployedOnVM(container, vm) == 0) {
                    TimeUnit.MILLISECONDS.sleep(container.getContainerImage().getDeployTime());
                }
                TimeUnit.MILLISECONDS.sleep(container.getContainerImage().getDeployTime());
            } else {
                log.info("Start Container: " + container + " on VM: " + vm);

                dockerControllerService.startContainer(vm, container);
                TimeUnit.MILLISECONDS.sleep(container.getContainerImage().getDeployTime());
            }
        } catch (InterruptedException | DockerException e) {
            log.error("EXCEPTION while deploying Container", e);

        }

        ContainerReportingAction report = new ContainerReportingAction(new Date(), container.getName(), vm.getName(), Action.START);
        reportDaoService.save(report);

    }

}
