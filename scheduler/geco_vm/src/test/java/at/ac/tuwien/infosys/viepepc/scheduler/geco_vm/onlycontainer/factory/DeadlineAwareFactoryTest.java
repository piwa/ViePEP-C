package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.factory;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.Element;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.service.ServiceTypeNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.GeCoVmApplication;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.OptimizationUtility;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.TestSchedulerGecoConfiguration;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.OrderMaintainer;
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
import java.util.concurrent.TimeUnit;
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
                "slack.webhook="
        })

@ActiveProfiles({"test", "GeCo_VM", "VmAndContainer"})
public class DeadlineAwareFactoryTest {

    @Autowired
    private DeadlineAwareFactory deadlineAwareFactory;
    @Autowired
    private WorkflowGenerationHelper workflowGenerationHelper;
    @Autowired
    private WorkflowUtilities workflowUtilities;

    OrderMaintainer orderMaintainer = new OrderMaintainer();

    @Value("${max.optimization.duration}")
    private long maxOptimizationDuration = 60000;
    @Value("${additional.optimization.time}")
    private long additionalOptimizationTime = 5000;

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
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

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
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

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
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_sequential_vmDeployed_containerDeployed() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
        WorkflowElement process = workflowElements.get(0);
        List<ProcessStep> nextProcessSteps = workflowUtilities.getNextSteps(process,null);

        ProcessStep processStep = nextProcessSteps.get(0);
        workflowGenerationHelper.set_vmDeployed_containerDeployed(processStep);
        Container container = processStep.getContainer();
        VirtualMachine vm = processStep.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep.getScheduledStartDate());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep.getInternId())).findFirst().orElseThrow(Exception::new);
        assertSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep.getInternId(), fixedGene.getProcessStep().getInternId());
        assertEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_sequential_vmDeploying_containerScheduled() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
        WorkflowElement process = workflowElements.get(0);
        List<ProcessStep> nextProcessSteps = workflowUtilities.getNextSteps(process,null);

        ProcessStep processStep = nextProcessSteps.get(0);
        workflowGenerationHelper.set_vmDeploying_containerScheduled(processStep);
        Container container = processStep.getContainer();
        VirtualMachine vm = processStep.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep.getScheduledStartDate());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep.getInternId())).findFirst().orElseThrow(Exception::new);
        assertNotSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep.getInternId(), fixedGene.getProcessStep().getInternId());
        assertNotEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertNotSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_sequential_vmDeployed_containerDeploying() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
        WorkflowElement process = workflowElements.get(0);
        List<ProcessStep> nextProcessSteps = workflowUtilities.getNextSteps(process,null);

        ProcessStep processStep = nextProcessSteps.get(0);
        workflowGenerationHelper.set_vmDeployed_containerDeploying(processStep);
        Container container = processStep.getContainer();
        VirtualMachine vm = processStep.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep.getScheduledStartDate());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep.getInternId())).findFirst().orElseThrow(Exception::new);
        assertSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep.getInternId(), fixedGene.getProcessStep().getInternId());
        assertEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }


    @Test
    public void generateRandomCandidate_parallel_vmDeployed_containerDeploying() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createParallelDifferentServicesProcess();
        WorkflowElement process = workflowElements.get(0);
        List<ProcessStep> nextProcessSteps = workflowUtilities.getNextSteps(process,null);

        ProcessStep processStep = nextProcessSteps.get(0);
        workflowGenerationHelper.set_vmDeployed_containerDeploying(processStep);
        Container container = processStep.getContainer();
        VirtualMachine vm = processStep.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep.getScheduledStartDate());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep.getInternId())).findFirst().orElseThrow(Exception::new);
        assertSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep.getInternId(), fixedGene.getProcessStep().getInternId());
        assertEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_parallel_vmDeployed_containerDeployed() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createParallelDifferentServicesProcess();
        WorkflowElement process = workflowElements.get(0);
        List<ProcessStep> nextProcessSteps = workflowUtilities.getNextSteps(process,null);

        ProcessStep processStep = nextProcessSteps.get(0);
        workflowGenerationHelper.set_vmDeployed_containerDeployed(processStep);
        Container container = processStep.getContainer();
        VirtualMachine vm = processStep.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep.getScheduledStartDate());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep.getInternId())).findFirst().orElseThrow(Exception::new);
        assertSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep.getInternId(), fixedGene.getProcessStep().getInternId());
        assertEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_parallel_vmDeploying_containerScheduled() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createParallelDifferentServicesProcess();
        WorkflowElement process = workflowElements.get(0);
        List<ProcessStep> nextProcessSteps = workflowUtilities.getNextSteps(process,null);

        ProcessStep processStep = nextProcessSteps.get(0);
        workflowGenerationHelper.set_vmDeploying_containerScheduled(processStep);
        Container container = processStep.getContainer();
        VirtualMachine vm = processStep.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep.getScheduledStartDate());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep.getInternId())).findFirst().orElseThrow(Exception::new);
        assertNotSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep.getInternId(), fixedGene.getProcessStep().getInternId());
        assertNotEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertNotSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }


    @Test
    public void generateRandomCandidate_sequential_parallel_vmDeployed_containerDeploying() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
        WorkflowElement process = workflowElements.get(0);
        List<ProcessStep> nextProcessSteps = workflowUtilities.getNextSteps(process,null);

        ProcessStep processStep = nextProcessSteps.get(0);
        workflowGenerationHelper.set_vmDeployed_containerDeploying(processStep);
        Container container = processStep.getContainer();
        VirtualMachine vm = processStep.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep.getScheduledStartDate());

        workflowElements.addAll(workflowGenerationHelper.createParallelDifferentServicesProcess());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep.getInternId())).findFirst().orElseThrow(Exception::new);
        assertSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep.getInternId(), fixedGene.getProcessStep().getInternId());
        assertEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_sequential_parallel_vmDeployed_containerDeployed() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
        WorkflowElement process = workflowElements.get(0);
        List<ProcessStep> nextProcessSteps = workflowUtilities.getNextSteps(process,null);

        ProcessStep processStep = nextProcessSteps.get(0);
        workflowGenerationHelper.set_vmDeployed_containerDeployed(processStep);
        Container container = processStep.getContainer();
        VirtualMachine vm = processStep.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep.getScheduledStartDate());

        workflowElements.addAll(workflowGenerationHelper.createParallelDifferentServicesProcess());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep.getInternId())).findFirst().orElseThrow(Exception::new);
        assertSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep.getInternId(), fixedGene.getProcessStep().getInternId());
        assertEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_sequential_parallel_vmDeploying_containerScheduled() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
        WorkflowElement process = workflowElements.get(0);
        List<ProcessStep> nextProcessSteps = workflowUtilities.getNextSteps(process,null);

        ProcessStep processStep = nextProcessSteps.get(0);
        workflowGenerationHelper.set_vmDeploying_containerScheduled(processStep);
        Container container = processStep.getContainer();
        VirtualMachine vm = processStep.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep.getScheduledStartDate());

        workflowElements.addAll(workflowGenerationHelper.createParallelDifferentServicesProcess());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep.getInternId())).findFirst().orElseThrow(Exception::new);
        assertNotSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep.getInternId(), fixedGene.getProcessStep().getInternId());
        assertNotEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertNotSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());

        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_sequential_parallel_onePSDone_vmDeployed_containerDeploying() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
        WorkflowElement process = workflowElements.get(0);
        List<Element> flattenWorkflowList = workflowUtilities.getFlattenWorkflow(new ArrayList<>(), process);

        ProcessStep processStep0 = (ProcessStep)flattenWorkflowList.get(1);
        DateTime serviceExecutionStartTime = DateTime.now().minusMinutes(2);
        DateTime serviceExecutionEndTime = DateTime.now().minusMinutes(1);
        processStep0.setStartDate(serviceExecutionStartTime);
        processStep0.setFinishedAt(serviceExecutionEndTime);
        processStep0.setScheduledStartDate(serviceExecutionStartTime);

        ProcessStep processStep1 = (ProcessStep)flattenWorkflowList.get(2);
        workflowGenerationHelper.set_vmDeployed_containerDeploying(processStep1);
        Container container = processStep1.getContainer();
        VirtualMachine vm = processStep1.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep1.getScheduledStartDate());

        workflowElements.addAll(workflowGenerationHelper.createParallelDifferentServicesProcess());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep1.getInternId())).findFirst().orElseThrow(Exception::new);

        assertSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep1.getInternId(), fixedGene.getProcessStep().getInternId());
        assertEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());
        assertNull(genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep0.getInternId())).findFirst().orElse(null));
        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_sequential_parallel_onePSDone_vmDeployed_containerDeployed() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
        WorkflowElement process = workflowElements.get(0);
        List<Element> flattenWorkflowList = workflowUtilities.getFlattenWorkflow(new ArrayList<>(), process);

        ProcessStep processStep0 = (ProcessStep)flattenWorkflowList.get(1);
        DateTime serviceExecutionStartTime = DateTime.now().minusMinutes(2);
        DateTime serviceExecutionEndTime = DateTime.now().minusMinutes(1);
        processStep0.setStartDate(serviceExecutionStartTime);
        processStep0.setFinishedAt(serviceExecutionEndTime);
        processStep0.setScheduledStartDate(serviceExecutionStartTime);

        ProcessStep processStep1 = (ProcessStep)flattenWorkflowList.get(2);
        workflowGenerationHelper.set_vmDeployed_containerDeployed(processStep1);
        Container container = processStep1.getContainer();
        VirtualMachine vm = processStep1.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep1.getScheduledStartDate());

        workflowElements.addAll(workflowGenerationHelper.createParallelDifferentServicesProcess());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep1.getInternId())).findFirst().orElseThrow(Exception::new);

        assertSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep1.getInternId(), fixedGene.getProcessStep().getInternId());
        assertEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());
        assertNull(genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep0.getInternId())).findFirst().orElse(null));
        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }

    @Test
    public void generateRandomCandidate_sequential_parallel_onePSDone_vmDeploying_containerScheduled() throws Exception {

        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
        WorkflowElement process = workflowElements.get(0);
        List<Element> flattenWorkflowList = workflowUtilities.getFlattenWorkflow(new ArrayList<>(), process);

        ProcessStep processStep0 = (ProcessStep)flattenWorkflowList.get(1);
        DateTime serviceExecutionStartTime = DateTime.now().minusMinutes(2);
        DateTime serviceExecutionEndTime = DateTime.now().minusMinutes(1);
        processStep0.setStartDate(serviceExecutionStartTime);
        processStep0.setFinishedAt(serviceExecutionEndTime);
        processStep0.setScheduledStartDate(serviceExecutionStartTime);

        ProcessStep processStep1 = (ProcessStep)flattenWorkflowList.get(2);
        workflowGenerationHelper.set_vmDeploying_containerScheduled(processStep1);
        Container container = processStep1.getContainer();
        VirtualMachine vm = processStep1.getContainer().getVirtualMachine();
        DateTime processStepScheduledStartTime = new DateTime(processStep1.getScheduledStartDate());

        workflowElements.addAll(workflowGenerationHelper.createParallelDifferentServicesProcess());

        deadlineAwareFactory.initialize(workflowElements, optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());

        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
        Chromosome.Gene fixedGene = genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep1.getInternId())).findFirst().orElseThrow(Exception::new);

        assertNotSame(container, fixedGene.getProcessStep().getContainerSchedulingUnit().getContainer());
        assertSame(processStep1.getInternId(), fixedGene.getProcessStep().getInternId());
        assertNotEquals(processStepScheduledStartTime.getMillis(), fixedGene.getExecutionInterval().getStartMillis());
        assertNotSame(vm, fixedGene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachine());
        assertNull(genes.stream().filter(gene -> gene.getProcessStep().getInternId().equals(processStep0.getInternId())).findFirst().orElse(null));
        checkAllGenesHaveAnAvailableVm_true(genes);
        checkIfAllVmIntervalsAreUsed_true(genes);
        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));

        log.info(chromosome.toString());
    }


    /******** Helper Methods ****/
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