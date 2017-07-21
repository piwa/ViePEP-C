package at.ac.tuwien.infosys.viepepc.serviceexecutor;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPDockerControllerService;
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
    private ViePEPCloudService viePEPCloudService;
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


//    @Async
    public void leaseVMAndStartExecutionOnVirtualMachine(VirtualMachine virtualMachine, List<ProcessStep> processSteps) {

        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String address = startVM(virtualMachine);

        VirtualMachineReportingAction report = new VirtualMachineReportingAction(DateTime.now(), virtualMachine.getInstanceId(), virtualMachine.getVmType().getIdentifier().toString(), Action.START);
        reportDaoService.save(report);

        if (address == null) {
            log.error("VM " + virtualMachine.getInstanceId() + " was not started, reset task");
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
            virtualMachine.setToBeTerminatedAt(new DateTime(virtualMachine.getStartedAt().getMillis() + virtualMachine.getVmType().getLeasingDuration()));

            startExecutionsOnVirtualMachine(processSteps, virtualMachine);
        }
    }

//    @Async
    public void leaseVMAndStartExecutionOnContainer(VirtualMachine virtualMachine, Map<Container, List<ProcessStep>> containerProcessSteps) {
        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        log.info("Start VM: " + virtualMachine.toString());
        StopWatch stopWatch2 = new StopWatch();
        stopWatch2.start("deploy vm");
        String address = startVM(virtualMachine);
        stopWatch2.stop();
        log.info("VM deploy duration: " + virtualMachine.toString() + ": " + stopWatch2.getTotalTimeMillis());

        VirtualMachineReportingAction report = new VirtualMachineReportingAction(DateTime.now(), virtualMachine.getInstanceId(), virtualMachine.getVmType().getIdentifier().toString(), Action.START);
        reportDaoService.save(report);

        if (address == null) {
            log.error("VM " + virtualMachine.getInstanceId() + " was not started, reset task");
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
            virtualMachine.setToBeTerminatedAt(new DateTime(virtualMachine.getStartedAt().getMillis() + virtualMachine.getVmType().getLeasingDuration()));
            startExecutionsOnContainer(containerProcessSteps, virtualMachine);

        }
    }

    public void startExecutionsOnVirtualMachine(final List<ProcessStep> processSteps, final VirtualMachine virtualMachine) {
        for (final ProcessStep processStep : processSteps) {
            serviceExecution.startExecution(processStep, virtualMachine);

        }
    }

    public void startExecutionsOnContainer(Map<Container, List<ProcessStep>> containerProcessSteps, VirtualMachine virtualMachine) {
        for (Map.Entry<Container, List<ProcessStep>> entry : containerProcessSteps.entrySet()) {

            log.info("Start Container: " + entry.getKey() + " on VM: " + virtualMachine);
            StopWatch stopWatch = new StopWatch();
            stopWatch.start("deploy container");

            deployContainer(virtualMachine, entry.getKey());

            stopWatch.stop();
            log.info("Container deploy duration: " + entry.getKey().toString() + ": " + stopWatch.getTotalTimeMillis());

            for (final ProcessStep processStep : entry.getValue()) {
                serviceExecution.startExecution(processStep, entry.getKey());
            }
        }
    }

    private String startVM(VirtualMachine virtualMachine) {
//        try {

            virtualMachine = viePEPCloudService.startVM(virtualMachine);
            log.info("VM up and running with ip: " + virtualMachine.getIpAddress() + " vm: " + virtualMachine);

//            TimeUnit.MILLISECONDS.sleep(virtualMachine.getVmType().getDeployTime());

//        } catch (InterruptedException e) {
//            log.error("EXCEPTION while starting VM", e);
//        }
        return virtualMachine.getIpAddress();
    }

    private void deployContainer(VirtualMachine vm, Container container) {
        if (container.isRunning()) {
            log.info(container + " already running on vm " + container.getVirtualMachine());
            return;
        }

        try {

//            log.info("Start Container: " + container + " on VM: " + vm);
//            StopWatch stopWatch = new StopWatch();
//            stopWatch.start("deploy container");
            dockerControllerService.startContainer(vm, container);
//            stopWatch.stop();
//            log.info("Container " + container.toString() + " deployed. Duration: " + stopWatch.getTotalTimeMillis());

//            TimeUnit.MILLISECONDS.sleep(container.getContainerImage().getDeployTime());

        } catch (InterruptedException | DockerException e) {
            log.error("EXCEPTION while deploying Container", e);

        }

        ContainerReportingAction report = new ContainerReportingAction(DateTime.now(), container.getName(), vm.getInstanceId(), Action.START);
        reportDaoService.save(report);

    }

}
