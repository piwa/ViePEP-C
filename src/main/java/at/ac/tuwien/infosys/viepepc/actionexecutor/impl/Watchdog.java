package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.actionexecutor.ViePEPCloudService;
import at.ac.tuwien.infosys.viepepc.database.entities.Action;
import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachineReportingAction;
import at.ac.tuwien.infosys.viepepc.database.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ProcessStepElementRepository;
import at.ac.tuwien.infosys.viepepc.database.externdb.services.ReportDaoService;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.watchdog.Message;
import at.ac.tuwien.infosys.viepepc.watchdog.Receiver;
import at.ac.tuwien.infosys.viepepc.watchdog.ServiceExecutionStatus;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class Watchdog {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private ViePEPCloudService viePEPCloudServiceImpl;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private Receiver receiver;
    @Autowired
    private ProcessStepElementRepository processStepElementRepository;
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

                List<ProcessStep> processStepList = new ArrayList<>();

                Set<Container> containers = vm.getDeployedContainers();
                containers.forEach(container -> processStepList.add(processStepElementRepository.findByContainerAndRunning(container.getId())));

                for(ProcessStep processStep : processStepList) {

                    processStep.reset();
                    processStepElementRepository.save(processStep);

                    processStep.getScheduledAtContainer().shutdownContainer();

                    ContainerReportingAction reportContainer = new ContainerReportingAction(DateTime.now(), processStep.getScheduledAtContainer().getName(), vm.getInstanceId(), Action.FAILED);
                    reportDaoService.save(reportContainer);
                }

                vm.terminate();

                VirtualMachineReportingAction reportVM = new VirtualMachineReportingAction(DateTime.now(), vm.getInstanceId(), vm.getVmType().getIdentifier().toString(), Action.FAILED);
                reportDaoService.save(reportVM);
            }


        }

    }


}
