package at.ac.tuwien.infosys.viepepc.actionexecutor;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.CloudControllerService;
import at.ac.tuwien.infosys.viepepc.cloudcontroller.DockerControllerService;
import at.ac.tuwien.infosys.viepepc.cloudcontroller.impl.exceptions.VmCouldNotBeStartedException;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheProcessStepService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.Action;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.ServiceExecution;
import at.ac.tuwien.infosys.viepepc.serviceexecutor.invoker.ServiceInvokeException;
import com.spotify.docker.client.exceptions.DockerException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.List;
import java.util.Map;

/**
 * Created by philippwaibel on 18/05/16.
 */
@Component
@Scope("prototype")
@Slf4j
public class WithVMDeploymentController {

    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private CloudControllerService cloudControllerService;
    @Autowired
    private ServiceExecution serviceExecution;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CacheProcessStepService cacheProcessStepService;
    @Autowired
    private DockerControllerService dockerControllerService;

    @Value("${simulate}")
    private boolean simulate;
    @Value("${use.container}")
    private boolean useDocker;

    @Async
    public void leaseVMAndStartExecutionOnContainer(VirtualMachineInstance virtualMachineInstance, Map<Container, List<ProcessStep>> containerProcessSteps) {

        try {
            final StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            log.info("Start VM: " + virtualMachineInstance.toString());
            StopWatch stopWatch2 = new StopWatch();
            stopWatch2.start("deploy vm");
            String address = startVM(virtualMachineInstance);
            stopWatch2.stop();
            log.info("VM deploy duration: " + virtualMachineInstance.toString() + ": " + stopWatch2.getTotalTimeMillis());

            if (address == null) {
                log.error("VM " + virtualMachineInstance.getInstanceId() + " was not started, reset task");
                for (Container container : containerProcessSteps.keySet()) {
                    for (ProcessStep processStep : containerProcessSteps.get(container)) {
                        processStep.setStartDate(null);
                    }
                    container.shutdownContainer();
                }
                return;
            } else {

                stopWatch.stop();

                if (virtualMachineInstance.getStartTime() == null) {
                    log.error("StartedAt is null");
                    virtualMachineInstance.setStartTime(DateTime.now());
                }
                startExecutionsOnContainer(containerProcessSteps, virtualMachineInstance);

            }
        } catch (VmCouldNotBeStartedException e) {
            log.error("VM could not be started. Stop VM and reset.", e);
            for (Map.Entry<Container, List<ProcessStep>> entry : containerProcessSteps.entrySet()) {
                resetContainer(entry.getKey(), virtualMachineInstance, "VM");
                resetProcessSteps(entry.getValue());
            }
            resetVM(virtualMachineInstance, "VM");
        }

    }


    public void startExecutionsOnContainer(Map<Container, List<ProcessStep>> containerProcessSteps, VirtualMachineInstance virtualMachineInstance) {
        for (Map.Entry<Container, List<ProcessStep>> entry : containerProcessSteps.entrySet()) {

            log.info("Start Container: " + entry.getKey() + " on VM: " + virtualMachineInstance);
            StopWatch stopWatch = new StopWatch();
            stopWatch.start("deploy container");

            boolean success = deployContainer(virtualMachineInstance, entry.getKey());

            if (success) {
                stopWatch.stop();
                log.info("Container deploy duration: " + entry.getKey().toString() + ": " + stopWatch.getTotalTimeMillis());

                for (final ProcessStep processStep : entry.getValue()) {
                    try {
                        serviceExecution.startExecution(processStep, entry.getKey());
                    } catch (ServiceInvokeException e) {
                        log.error("Exception while invoking service. Stop VM and reset.", e);
                        reset(entry.getValue(), entry.getKey(), virtualMachineInstance, "Service");
                    }
                }
            } else {
                reset(entry.getValue(), entry.getKey(), virtualMachineInstance, "Container");
            }
        }
    }

    private String startVM(VirtualMachineInstance virtualMachineInstance) throws VmCouldNotBeStartedException {

//        Object waitObject = cacheVirtualMachineService.getVmDeployedWaitObjectMap().get(virtualMachineInstance);
//        if (waitObject == null) {
//            waitObject = new Object();
//            cacheVirtualMachineService.getVmDeployedWaitObjectMap().put(virtualMachineInstance, waitObject);
//
//            try {
//                virtualMachineInstance = cloudControllerService.deployVM(virtualMachineInstance);
//            } catch (VmCouldNotBeStartedException e) {
//                synchronized (waitObject) {
//                    waitObject.notifyAll();
//                }
//                cacheVirtualMachineService.getVmDeployedWaitObjectMap().remove(virtualMachineInstance);
//                throw e;
//            }
//
//            log.info("VM up and running with ip: " + virtualMachineInstance.getIpAddress() + " vm: " + virtualMachineInstance);
//            VirtualMachineReportingAction report = new VirtualMachineReportingAction(virtualMachineInstance.getStartTime(), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.START);
//            reportDaoService.save(report);
//
//            synchronized (waitObject) {
//                waitObject.notifyAll();
//            }
//            cacheVirtualMachineService.getVmDeployedWaitObjectMap().remove(virtualMachineInstance);
//        } else {
//            try {
//                synchronized (waitObject) {
//                    waitObject.wait();
//                }
//            } catch (InterruptedException e) {
//                log.error("Exception", e);
//            }
//            if (!virtualMachineInstance.getVirtualMachineStatus().equals(VirtualMachineStatus.DEPLOYED)) {
//                throw new VmCouldNotBeStartedException("VM could not be started");
//            }
//        }

        return virtualMachineInstance.getIpAddress();
    }

    private boolean deployContainer(VirtualMachineInstance vm, Container container) {
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
            return false;
        }
    }


    private void reset(List<ProcessStep> value, Container container, VirtualMachineInstance vm, String failureReason) {

        resetContainer(container, vm, failureReason);
        resetProcessSteps(value);
        resetVM(vm, failureReason);
    }

    private void resetProcessSteps(List<ProcessStep> value) {
        for (ProcessStep processStep : value) {
            // TODO
//            cacheProcessStepService.getProcessStepsWaitingForExecution().remove(processStep);
//            cacheProcessStepService.getProcessStepsWaitingForServiceDone().remove(processStep.getName());
            processStep.reset();
        }
    }

    private void resetContainer(Container container, VirtualMachineInstance vm, String failureReason) {
        if (container != null) {
            ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), container.getName(), container.getContainerConfiguration().getName(), vm.getInstanceId(), Action.FAILED, failureReason);
            reportDaoService.save(reportContainer);
            container.shutdownContainer();
        }
    }

    private void resetVM(VirtualMachineInstance virtualMachineInstance, String failureReason) {
        VirtualMachineReportingAction reportVM = new VirtualMachineReportingAction(DateTime.now(), virtualMachineInstance.getInstanceId(), virtualMachineInstance.getVmType().getIdentifier().toString(), Action.FAILED, failureReason);
        reportDaoService.save(reportVM);

        log.info("Terminate: " + virtualMachineInstance);

        cloudControllerService.stopVirtualMachine(virtualMachineInstance);
        virtualMachineInstance.terminate();
    }

}
