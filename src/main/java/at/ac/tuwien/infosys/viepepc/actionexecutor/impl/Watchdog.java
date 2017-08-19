package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.database.entities.Action;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheProcessStepService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

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

    @Value("${messagebus.queue.name}")
    private String queueName;

    @Scheduled(initialDelay=10000, fixedDelay=10000)        // fixedRate
    public void monitor() {
        Set<VirtualMachine> virtualMachineList = cacheVirtualMachineService.getStartedVMs();

        for(VirtualMachine vm : virtualMachineList) {
            boolean available = viePEPCloudServiceImpl.checkAvailabilityOfDockerhost(vm);

            if(!available) {

                log.error("VM not available anymore. Reset execution request. " + vm.toString());

                Set<ProcessStep> processSteps = new HashSet<>();

                Set<Container> containers = vm.getDeployedContainers();
                containers.forEach(container -> processSteps.addAll(cacheProcessStepService.findByContainerAndRunning(container)));

                for(ProcessStep processStep : processSteps) {

                    ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), processStep.getScheduledAtContainer().getName(), vm.getInstanceId(), Action.FAILED);
                    reportDaoService.save(reportContainer);

                    processStep.getScheduledAtContainer().shutdownContainer();

                    inMemoryCache.getProcessStepsWaitingForServiceDone().remove(processStep.getName());
                    processStep.reset();
                }

                VirtualMachineReportingAction reportVM = new VirtualMachineReportingAction(DateTime.now(), vm.getInstanceId(), vm.getVmType().getIdentifier().toString(), Action.FAILED);
                reportDaoService.save(reportVM);

                vm.setIpAddress(null);
                vm.terminate();

            }
        }

    }


}
