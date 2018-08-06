package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.database.entities.Action;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheProcessStepService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheWorkflowService;
import at.ac.tuwien.infosys.viepepc.reasoner.PlacementHelper;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class Watchdog {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private ViePEPCloudService viePEPCloudServiceImpl;
    @Autowired
    private CacheProcessStepService cacheProcessStepService;
    @Autowired
    private InMemoryCacheImpl inMemoryCache;
    @Autowired
    private ReportDaoService reportDaoService;
    @Autowired
    private PlacementHelper placementHelper;
    @Autowired
    private CacheWorkflowService cacheWorkflowService;

    public static Object SYNC_OBJECT = new Object();

    @Value("${messagebus.queue.name}")
    private String queueName;

    @Scheduled(initialDelay=60000, fixedDelay=60000)        // fixedRate
    public void monitor() {

        log.info("Start Watchdog Iteration");

        synchronized (SYNC_OBJECT) {

            Set<VirtualMachine> virtualMachineList = cacheVirtualMachineService.getStartedVMs();

            for (VirtualMachine vm : virtualMachineList) {

                if (!vm.isTerminating()) {

                    boolean available = false;
                    int sleepTimer = 0;
                    for(int i = 0; i < 3; i++) {
                        available = viePEPCloudServiceImpl.checkAvailabilityOfDockerhost(vm);
                        if(available) {
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

                        for (Element element : placementHelper.getRunningSteps()) {
                            ProcessStep processStep = (ProcessStep) element;
                            getContainersAndProcesses(vm, processSteps, containers, processStep);
                        }

                        inMemoryCache.getProcessStepsWaitingForServiceDone().values().forEach(processStep -> getContainersAndProcesses(vm, processSteps, containers, processStep));

                        inMemoryCache.getWaitingForExecutingProcessSteps().forEach(processStep -> getContainersAndProcesses(vm, processSteps, containers, processStep));

                        processSteps.forEach(processStep -> log.warn("reset process step: " + processStep.toString()));
                        processSteps.forEach(processStep -> resetContainerAndProcessStep(vm, processStep, "VM"));
                        resetVM(vm, "VM");

                    }
                }
            }


            try {
                List<ProcessStep> processSteps = getAllRunningSteps();

                for (ProcessStep processStep : processSteps) {
                    if (processStep.getStartDate() != null && processStep.getServiceType() != null && processStep.getScheduledAtContainer() != null && processStep.getScheduledAtContainer().getVirtualMachine() != null) {
                        long maxDuration = processStep.getServiceType().getServiceTypeResources().getMakeSpan() * 10;
                        if (processStep.getStartDate().plus(maxDuration).isBeforeNow()) {
                            log.warn("Reset process step due to unexpected long execution: " + processStep.toString());
                            resetContainerAndProcessStep(processStep.getScheduledAtContainer().getVirtualMachine(), processStep, "Service");
                            resetVM(processStep.getScheduledAtContainer().getVirtualMachine(), "Service");
                        }
                    }
                }
            }
            catch (Exception ex) {
                // ignore
            }

        }

        log.info("Done Watchdog Iteration");

    }

    private void resetVM(VirtualMachine vm, String reason) {
        VirtualMachineReportingAction reportVM = new VirtualMachineReportingAction(DateTime.now(), vm.getInstanceId(), vm.getVmType().getIdentifier().toString(), Action.FAILED, reason);
        reportDaoService.save(reportVM);

        viePEPCloudServiceImpl.stopVirtualMachine(vm);

        vm.terminate();
    }

    private void resetContainerAndProcessStep(VirtualMachine vm, ProcessStep processStep, String reason) {
        ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), processStep.getScheduledAtContainer().getName(), vm.getInstanceId(), Action.FAILED, reason);
        reportDaoService.save(reportContainer);

        inMemoryCache.getProcessStepsWaitingForServiceDone().remove(processStep.getName());
        inMemoryCache.getWaitingForExecutingProcessSteps().remove(processStep);
        processStep.getScheduledAtContainer().shutdownContainer();
        processStep.reset();
    }


    private List<ProcessStep> getAllRunningSteps() {
        List<WorkflowElement> workflows = Collections.synchronizedList(cacheWorkflowService.getRunningWorkflowInstances());
        Set<ProcessStep> runningProcesses = new HashSet<>();

        workflows.forEach(workflowElement -> runningProcesses.addAll(placementHelper.getRunningProcessSteps(workflowElement.getName())));
        return Collections.synchronizedList(new ArrayList<>(runningProcesses));
    }


    private void getContainersAndProcesses(VirtualMachine vm, Set<ProcessStep> processSteps, Set<Container> containers, ProcessStep processStep) {
        if(containers.contains(processStep.getScheduledAtContainer())) {
            processSteps.add(processStep);
        }

        if(processStep.getScheduledAtContainer().getVirtualMachine() == vm) {
            containers.add(processStep.getScheduledAtContainer());
            processSteps.add(processStep);
        }
    }


}
