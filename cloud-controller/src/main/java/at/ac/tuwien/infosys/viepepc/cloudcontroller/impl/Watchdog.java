package at.ac.tuwien.infosys.viepepc.cloudcontroller.impl;

import at.ac.tuwien.infosys.viepepc.cloudcontroller.CloudControllerService;
import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheProcessStepService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.library.entities.Action;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class Watchdog {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private CloudControllerService cloudControllerServiceImpl;
    @Autowired
    private CacheProcessStepService cacheProcessStepService;
    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private WorkflowUtilities workflowUtilities;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;
    @Autowired
    private CacheProcessStepService processStepService;

    public static Object SYNC_OBJECT = new Object();

    @Value("${messagebus.queue.name}")
    private String queueName;

    //    @Scheduled(initialDelay=60000, fixedDelay=60000)        // fixedRate
    public void monitor() {

        log.info("Start Watchdog Iteration");

        synchronized (SYNC_OBJECT) {

            List<VirtualMachineInstance> virtualMachineInstanceList = cacheVirtualMachineService.getDeployedVMInstances();

            for (VirtualMachineInstance vm : virtualMachineInstanceList) {

                boolean available = false;
                int sleepTimer = 0;
                for (int i = 0; i < 3; i++) {
                    available = cloudControllerServiceImpl.checkAvailabilityOfDockerhost(vm);
                    if (available) {
                        break;
                    }

                    try {
                        sleepTimer = sleepTimer + 10;
                        TimeUnit.SECONDS.sleep(sleepTimer);
                    } catch (InterruptedException e) {
                    }
                }

                if (!available) {

                    log.error("VM not available anymore. Reset execution request. " + vm.toString());

                    Set<ProcessStep> processSteps = new HashSet<>();

                    Set<Container> containers = vm.getDeployedContainers();
                    containers.forEach(container -> processSteps.addAll(cacheProcessStepService.findByContainerAndRunning(container)));

                    for (Element element : workflowUtilities.getRunningSteps()) {
                        ProcessStep processStep = (ProcessStep) element;
                        getContainersAndProcesses(vm, processSteps, containers, processStep);
                    }

                    // TODO
//                    processStepService.getProcessStepsWaitingForServiceDone().values().forEach(processStep -> getContainersAndProcesses(vm, processSteps, containers, processStep));
//                    processStepService.getProcessStepsWaitingForExecution().forEach(processStep -> getContainersAndProcesses(vm, processSteps, containers, processStep));

                    processSteps.forEach(processStep -> log.warn("reset process step: " + processStep.toString()));
                    processSteps.forEach(processStep -> resetContainerAndProcessStep(vm, processStep, "VM"));
                    resetVM(vm, "VM");

                }
            }
        }


        try {
            List<ProcessStep> processSteps = getAllRunningSteps();

            for (ProcessStep processStep : processSteps) {
                if (processStep.getStartDate() != null && processStep.getServiceType() != null && processStep.getContainer() != null && processStep.getContainer().getVirtualMachineInstance() != null) {
                    long maxDuration = processStep.getServiceType().getServiceTypeResources().getMakeSpan() * 5;
                    if (processStep.getStartDate().plus(maxDuration).isBeforeNow()) {
                        log.warn("Reset process step due to unexpected long execution: " + processStep.toString());
                        resetContainerAndProcessStep(processStep.getContainer().getVirtualMachineInstance(), processStep, "Service");
//                            resetVM(processStep.get().getVirtualMachineInstance(), "Service");
                    }
                }
            }
        } catch (Exception ex) {
            // ignore
        }


        log.info("Done Watchdog Iteration");

    }

    private void resetVM(VirtualMachineInstance vm, String reason) {
        VirtualMachineReportingAction reportVM = new VirtualMachineReportingAction(DateTime.now(), vm.getInstanceId(), vm.getVmType().getIdentifier().toString(), Action.FAILED, reason);
        reportDaoService.save(reportVM);

        cloudControllerServiceImpl.stopVirtualMachine(vm);

        vm.terminate();
    }

    private void resetContainerAndProcessStep(VirtualMachineInstance vm, ProcessStep processStep, String reason) {
        ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), processStep.getContainer().getName(), processStep.getContainer().getContainerConfiguration().getName(), vm.getInstanceId(), Action.FAILED, reason);
        reportDaoService.save(reportContainer);

        // TODO
//        cacheProcessStepService.getProcessStepsWaitingForServiceDone().remove(processStep.getName());
//        cacheProcessStepService.getProcessStepsWaitingForExecution().remove(processStep);
        processStep.getContainer().shutdownContainer();
        processStep.reset();
    }


    private List<ProcessStep> getAllRunningSteps() {
        List<WorkflowElement> workflows = Collections.synchronizedList(cacheWorkflowService.getRunningWorkflowInstances());
        Set<ProcessStep> runningProcesses = new HashSet<>();

        workflows.forEach(workflowElement -> runningProcesses.addAll(workflowUtilities.getRunningProcessSteps(workflowElement.getName())));
        return Collections.synchronizedList(new ArrayList<>(runningProcesses));
    }


    private void getContainersAndProcesses(VirtualMachineInstance vm, Set<ProcessStep> processSteps, Set<Container> containers, ProcessStep processStep) {
        if (containers.contains(processStep.getContainer())) {
            processSteps.add(processStep);
        }

        if (processStep.getContainer().getVirtualMachineInstance() == vm) {
            containers.add(processStep.getContainer());
            processSteps.add(processStep);
        }
    }


}
