package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.operations;

import at.ac.tuwien.infosys.viepepc.database.WorkflowUtilities;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineStatus;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.GeCoVmApplication;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.TestSchedulerGecoConfiguration;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.factory.DeadlineAwareFactory;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.factory.WorkflowGenerationHelper;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Random;
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
public class FitnessFunctionTest {

    @Autowired
    private DeadlineAwareFactory deadlineAwareFactory;
    @Autowired
    private WorkflowGenerationHelper workflowGenerationHelper;
    @Autowired
    private WorkflowUtilities workflowUtilities;
    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private FitnessFunction fitnessFunction;
    OrderMaintainer orderMaintainer = new OrderMaintainer();

    @Value("${max.optimization.duration}")
    private long maxOptimizationDuration = 60000;
    @Value("${additional.optimization.time}")
    private long additionalOptimizationTime = 5000;

    private DateTime optimizationEndTime;

    @Before
    public void initialize() {
        this.optimizationEndTime = DateTime.now().plus(this.maxOptimizationDuration).plus(this.additionalOptimizationTime);
        this.fitnessFunction.setOptimizationEndTime(this.optimizationEndTime);
    }

    @Test
    public void getFitness() throws Exception {

        deadlineAwareFactory.initialize(workflowGenerationHelper.createSequentialProcess(), optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());
        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());

        long totalGeneExecutionDuration = 0;
        for (Chromosome.Gene gene : genes) {
            VirtualMachineInstance vm = cacheVirtualMachineService.getNewVirtualMachineInstance(2);
            gene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachineInstance().setVmType(vm.getVmType());
            totalGeneExecutionDuration = totalGeneExecutionDuration + gene.getExecutionInterval().toDuration().getStandardSeconds();
        }

        assertEquals(38975.25, fitnessFunction.getFitness(chromosome, null),0.0);
        assertEquals(840, totalGeneExecutionDuration, 0.0);
        assertEquals(38975.25, fitnessFunction.getLeasingCost(), 0.0);
        assertEquals(0.0, fitnessFunction.getPenaltyCost(), 0.0);
    }

    @Test
    public void getFitnessWithUnusedVMs() throws Exception {

        deadlineAwareFactory.initialize(workflowGenerationHelper.createSequentialProcess(), optimizationEndTime);
        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());
        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());

        long totalGeneExecutionDuration = 0;
        for (Chromosome.Gene gene : genes) {
            VirtualMachineInstance vm = cacheVirtualMachineService.getNewVirtualMachineInstance(2);
            gene.getProcessStep().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachineInstance().setVmType(vm.getVmType());
            totalGeneExecutionDuration = totalGeneExecutionDuration + gene.getExecutionInterval().toDuration().getStandardSeconds();
        }

        VirtualMachineInstance virtualMachineInstance = cacheVirtualMachineService.getNewVirtualMachineInstance(2);
        virtualMachineInstance.setDeploymentStartTime(this.optimizationEndTime.minusMinutes(10));
        virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYED);
        cacheVirtualMachineService.getAllVMInstancesFromInMemory().add(virtualMachineInstance);

        assertEquals(62525.25, fitnessFunction.getFitness(chromosome, null),0.0);
        assertEquals(840, totalGeneExecutionDuration, 0.0);
        assertEquals(62525.25, fitnessFunction.getLeasingCost(), 0.0);
        assertEquals(0.0, fitnessFunction.getPenaltyCost(), 0.0);
    }
}