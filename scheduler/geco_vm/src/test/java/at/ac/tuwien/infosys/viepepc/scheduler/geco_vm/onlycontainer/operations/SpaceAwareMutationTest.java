package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.operations;

import at.ac.tuwien.infosys.viepepc.library.registry.impl.service.ServiceTypeNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.GeCoVmApplication;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.TestSchedulerGecoConfiguration;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.Chromosome;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.OrderMaintainer;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.VMSelectionHelper;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.onlycontainer.entities.VirtualMachineSchedulingUnit;
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
import org.uncommons.maths.random.MersenneTwisterRNG;
import org.uncommons.maths.random.PoissonGenerator;

import javax.xml.bind.JAXBException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {GeCoVmApplication.class, TestSchedulerGecoConfiguration.class},
        properties = {"evaluation.prefix=develop",
                "profile.specific.database.name=geco_vm",
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
public class SpaceAwareMutationTest {

    @Autowired
    private DeadlineAwareFactory deadlineAwareFactory;
    @Autowired
    private WorkflowGenerationHelper workflowGenerationHelper;
    @Autowired
    private VMSelectionHelper vmSelectionHelper;

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
    public void createMutation_sequentialProcess() throws JAXBException, ServiceTypeNotFoundException {
        deadlineAwareFactory.initialize(workflowGenerationHelper.createSequentialProcess(), optimizationEndTime);
        performTest();
    }

    @Test
    public void createMutation_parallelDifferentServicesProcess() throws JAXBException, ServiceTypeNotFoundException {
        deadlineAwareFactory.initialize(workflowGenerationHelper.createParallelDifferentServicesProcess(), optimizationEndTime);
        performTest();
    }

    @Test
    public void createMutation_parallelSameServicesProcess() throws JAXBException, ServiceTypeNotFoundException {
        deadlineAwareFactory.initialize(workflowGenerationHelper.createParallelSameServicesProcess(), optimizationEndTime);
        performTest();
    }

    @Test
    public void createMutation_allWorkflowElements() throws JAXBException, ServiceTypeNotFoundException {
        deadlineAwareFactory.initialize(workflowGenerationHelper.createAllWorkflowElements(), optimizationEndTime);
        performTest();
    }

    private void performTest() {
        SpaceAwareMutation spaceAwareMutation = new SpaceAwareMutation(new PoissonGenerator(4, new MersenneTwisterRNG()), optimizationEndTime, deadlineAwareFactory.getMaxTimeAfterDeadline());

        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());
        List<Chromosome.Gene> originalGenes = chromosome.getFlattenChromosome();

        List<Chromosome> chromosomeList = new ArrayList<>();
        chromosomeList.add(chromosome);
        List<Chromosome> mutatedChromosomes = spaceAwareMutation.apply(chromosomeList, new Random());
        assertThat(mutatedChromosomes.size(), is(1));

        List<Chromosome.Gene> mutatedGenes = mutatedChromosomes.get(0).getFlattenChromosome();

        int changes = 0;

        for (Chromosome.Gene originalGene : originalGenes) {
            for (Chromosome.Gene mutatedGene : mutatedGenes) {
                if (originalGene.getProcessStepSchedulingUnit().getInternId().equals(mutatedGene.getProcessStepSchedulingUnit().getInternId()) &&
                        originalGene.getExecutionInterval().getStartMillis() != mutatedGene.getExecutionInterval().getStartMillis() &&
                        originalGene.getExecutionInterval().getEndMillis() != mutatedGene.getExecutionInterval().getEndMillis()) {
                    changes = changes + 1;
                }
            }
        }
        assertThat(changes, is(1));

        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = mutatedGenes.stream().map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm()).collect(Collectors.toSet());
        virtualMachineSchedulingUnits.forEach(unit -> assertTrue(vmSelectionHelper.checkEnoughResourcesLeftOnVM(unit)));

        assertTrue(orderMaintainer.orderIsOk(chromosome.getGenes()));
    }
}






























