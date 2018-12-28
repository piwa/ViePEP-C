package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.factory;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.service.ServiceTypeNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.GeCoVmApplication;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.TestSchedulerGecoConfiguration;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.Chromosome;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import javax.xml.bind.JAXBException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {GeCoVmApplication.class, TestSchedulerGecoConfiguration.class},
        properties = {"evaluation.prefix=develop",
                "profile.specific.database.name=onlycontainergeneticalgorithm",
                "evaluation.suffix=1",
                "min.optimization.interval.ms = 20000",
                "vm.simulation.deploy.duration.average=53819",
                "vm.simulation.deploy.duration.stddev=8504",
                "simulate = true",
                "container.imageNotAvailable.simulation.deploy.duration.average=0",
                "container.imageNotAvailable.simulation.deploy.duration.stddev=0",
                "container.imageAvailable.simulation.deploy.duration.average=0",
                "container.imageAvailable.simulation.deploy.duration.stddev=0",
                "slack.webhook=asdf"
        })

@ActiveProfiles({"test", "GeCo_VM", "VmAndContainer"})
public class DeadlineAwareFactoryTest {

    @Autowired
    private DeadlineAwareFactory deadlineAwareFactory;
    @Autowired
    private WorkflowGenerationHelper workflowGenerationHelper;
    @Autowired
    private WorkflowUtilities workflowUtilities;
    @Autowired
    private OptimizationUtility optimizationUtility;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;

    @Value("${max.optimization.duration}")
    private long maxOptimizationDuration = 60000;
    @Value("${additional.optimization.time}")
    private long additionalOptimizationTime = 5000;
    @Value("${container.default.deploy.time}")
    private long containerDeploymentTime;
    @Value("${virtual.machine.default.deploy.time}")
    private long virtualMachineDeploymentTime;

    private DateTime optimizationEndTime;

    @Before
    public void initFactory() {
        optimizationEndTime = DateTime.now().plus(maxOptimizationDuration).plus(additionalOptimizationTime);
    }

    @Test
    public void generateRandomCandidate_parallelProcessSameServices() throws JAXBException, ServiceTypeNotFoundException {
        deadlineAwareFactory.initialize(workflowGenerationHelper.createParallelSameServicesProcess(), optimizationEndTime);

        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());

        genes.forEach(gene -> assertNotNull(gene.getProcessStep()));
        genes.stream().map(Chromosome.Gene::getProcessStep).forEach(schedulingUnit -> assertNotNull(schedulingUnit.getContainerSchedulingUnit()));
        genes.stream().map(Chromosome.Gene::getProcessStep).forEach(schedulingUnit -> assertEquals(3, schedulingUnit.getContainerSchedulingUnit().getProcessStepGenes().size()));
        genes.stream().map(gene -> gene.getProcessStep().getContainerSchedulingUnit()).forEach(schedulingUnit -> assertNotNull(schedulingUnit.getScheduledOnVm()));
        genes.stream().map(gene -> gene.getProcessStep().getContainerSchedulingUnit()).forEach(schedulingUnit -> assertEquals(1, schedulingUnit.getScheduledOnVm().getScheduledContainers().size()));

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);

        log.info(chromosome.toString());

    }

    @Test
    public void generateRandomCandidate_parallelProcessDifferentServices() throws JAXBException, ServiceTypeNotFoundException {
        deadlineAwareFactory.initialize(workflowGenerationHelper.createParallelDifferentServicesProcess(), optimizationEndTime);

        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());

        genes.forEach(gene -> assertNotNull(gene.getProcessStep()));
        genes.stream().map(Chromosome.Gene::getProcessStep).forEach(schedulingUnit -> assertNotNull(schedulingUnit.getContainerSchedulingUnit()));
        genes.stream().map(Chromosome.Gene::getProcessStep).forEach(schedulingUnit -> assertEquals(1, schedulingUnit.getContainerSchedulingUnit().getProcessStepGenes().size()));
        genes.stream().map(gene -> gene.getProcessStep().getContainerSchedulingUnit()).forEach(schedulingUnit -> assertNotNull(schedulingUnit.getScheduledOnVm()));

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_sequentialProcessDifferentServices() throws JAXBException, ServiceTypeNotFoundException {
        deadlineAwareFactory.initialize(workflowGenerationHelper.createSequentialProcess(), optimizationEndTime);

        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());

        genes.forEach(gene -> assertNotNull(gene.getProcessStep()));
        genes.stream().map(Chromosome.Gene::getProcessStep).forEach(schedulingUnit -> assertNotNull(schedulingUnit.getContainerSchedulingUnit()));
        genes.stream().map(Chromosome.Gene::getProcessStep).forEach(schedulingUnit -> assertEquals(1, schedulingUnit.getContainerSchedulingUnit().getProcessStepGenes().size()));
        genes.stream().map(gene -> gene.getProcessStep().getContainerSchedulingUnit()).forEach(schedulingUnit -> assertNotNull(schedulingUnit.getScheduledOnVm()));

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_duringExecution() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
        WorkflowElement process = workflowElements.get(0);
        List<ProcessStep> nextProcessSteps = workflowUtilities.getNextSteps(process,null);

        ProcessStep processStep = nextProcessSteps.get(0);

        DateTime serviceExecutionStartTime = DateTime.now();
        Interval containerScheduledAvailableInterval = new Interval(serviceExecutionStartTime, serviceExecutionStartTime.plus(processStep.getServiceType().getServiceTypeResources().getMakeSpan()));
        Interval vmScheduledAvailableInterval = new Interval(containerScheduledAvailableInterval.getStart().minus(this.containerDeploymentTime), serviceExecutionStartTime.plus(processStep.getServiceType().getServiceTypeResources().getMakeSpan()));

        processStep.setStartDate(serviceExecutionStartTime);
        processStep.setScheduledStartDate(serviceExecutionStartTime);

        Container container = optimizationUtility.getContainer(processStep.getServiceType(), 1);
        container.setScheduledAvailableInterval(containerScheduledAvailableInterval);
        container.setStartDate(containerScheduledAvailableInterval.getStart());
        container.setContainerStatus(ContainerStatus.DEPLOYED);

        log.info(container.toString());

        VMType vmType = cacheVirtualMachineService.getVmTypeFromCore(2);
        VirtualMachine vm = cacheVirtualMachineService.getVMs(vmType).get(0);
        Map<UUID, Interval> vmIntervals = new HashMap<>();
        vmIntervals.put(UUID.randomUUID(), vmScheduledAvailableInterval);
        vm.setScheduledAvailableIntervals(vmIntervals);
        vm.setStartDate(vmScheduledAvailableInterval.getStart());
        vm.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYED);

        container.setVirtualMachine(vm);
        processStep.setContainer(container);

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        assertSame(container, genes.get(0).getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep.getInternId(), genes.get(0).getProcessStep().getInternId());
        assertSame(vm, genes.get(0).getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());

        log.info(chromosome.toString());

    }

    private void checkAllGenesHaveAnAvailableVm_true(List<Chromosome.Gene> genes) {
        for (Chromosome.Gene gene : genes) {
            Interval geneExecutionInterval = gene.getExecutionInterval();
            List<Interval> vmAvailableTimes = gene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVmAvailableTimes();
            boolean intervalFits = false;
            for (Interval vmAvailableTime : vmAvailableTimes) {
                if (vmAvailableTime.contains(geneExecutionInterval)) {
                    intervalFits = true;
                }
            }
            assertTrue(intervalFits);
        }
    }

    private void checkIfAllVmIntervalsAreUsed_true(List<Chromosome.Gene> genes) {
        Set<Interval> vmAvailableIntervals = genes.stream().map(gene -> gene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVmAvailableTimes()).flatMap(List::stream).collect(Collectors.toSet());

        for (Iterator<Interval> iterator = vmAvailableIntervals.iterator(); iterator.hasNext(); ) {
            Interval vmAvailableInterval = iterator.next();
            for (Chromosome.Gene gene : genes) {
                if (vmAvailableInterval.contains(gene.getExecutionInterval())) {
                    iterator.remove();
                    break;
                }
            }
        }

        assertEquals(0, vmAvailableIntervals.size());

    }
}