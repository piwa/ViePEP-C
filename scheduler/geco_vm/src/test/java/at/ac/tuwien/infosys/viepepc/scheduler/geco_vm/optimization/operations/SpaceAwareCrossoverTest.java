package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.GeCoVmApplication;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.TestSchedulerGecoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

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
public class SpaceAwareCrossoverTest {
//
//    @Autowired
//    private DeadlineAwareFactory deadlineAwareFactory;
//    @Autowired
//    private WorkflowGenerationHelper workflowGenerationHelper;
//    @Autowired
//    private VMSelectionHelper vmSelectionHelper;
//
//    OrderMaintainer orderMaintainer = new OrderMaintainer();
//
//    @Value("${max.optimization.duration}")
//    private long maxOptimizationDuration = 60000;
//    @Value("${additional.optimization.time}")
//    private long additionalOptimizationTime = 5000;
//
//    private DateTime optimizationEndTime;
//
//    @Before
//    public void initFactory() {
//        optimizationEndTime = DateTime.now().plus(maxOptimizationDuration).plus(additionalOptimizationTime);
//    }
//
//    @Test
//    public void performCrossover_sequentialProcess() throws JAXBException, ServiceTypeNotFoundException {
//        deadlineAwareFactory.initialize(workflowGenerationHelper.createSequentialProcess(), optimizationEndTime);
//        performTest();
//    }
//
//    @Test
//    public void performCrossover_parallelDifferentServicesProcess() throws JAXBException, ServiceTypeNotFoundException {
//        deadlineAwareFactory.initialize(workflowGenerationHelper.createParallelDifferentServicesProcess(), optimizationEndTime);
//        performTest();
//    }
//
//    @Test
//    public void performCrossover_parallelSameServicesProcess() throws JAXBException, ServiceTypeNotFoundException {
//        deadlineAwareFactory.initialize(workflowGenerationHelper.createParallelSameServicesProcess(), optimizationEndTime);
//        performTest();
//    }
//
//    @Test
//    public void performCrossover_allWorkflowElements() throws JAXBException, ServiceTypeNotFoundException {
//        deadlineAwareFactory.initialize(workflowGenerationHelper.createAllWorkflowElements(), optimizationEndTime);
//        performTest();
//    }
//
//    private void performTest() {
//        Chromosome chromosome_1 = deadlineAwareFactory.generateRandomCandidate(new Random());
//        Chromosome chromosome_2 = deadlineAwareFactory.generateRandomCandidate(new Random());
//
//        SpaceAwareCrossover spaceAwareCrossover = new SpaceAwareCrossover(deadlineAwareFactory.getMaxTimeAfterDeadline());
//
//        List<Chromosome> crossoverChromosomes = spaceAwareCrossover.mate(chromosome_1, chromosome_2, 1, new Random());
//
//        boolean crossoverFound = false;
//        for (int row = 0; row < chromosome_1.getRowAmount(); row++) {
//
//            List<Chromosome.Gene> origin_chromosome_1_genes = chromosome_1.getRow(row);
//            List<Chromosome.Gene> origin_chromosome_2_genes = chromosome_2.getRow(row);
//
//            List<Chromosome.Gene> new_chromosome_1_genes = crossoverChromosomes.get(0).getRow(row);
//            List<Chromosome.Gene> new_chromosome_2_genes = crossoverChromosomes.get(1).getRow(row);
//
//            int crossoverPoint = -1;
//            for (int i = 0; i < new_chromosome_1_genes.size(); i++) {
//                Chromosome.Gene newGene = new_chromosome_1_genes.get(i);
//                Chromosome.Gene originGene = origin_chromosome_1_genes.get(i);
//                if (newGene.getProcessStepSchedulingUnit().getUid().equals(originGene.getProcessStepSchedulingUnit().getUid()) &&
//                        newGene.getExecutionInterval().getStartMillis() != originGene.getExecutionInterval().getStartMillis() &&
//                        newGene.getExecutionInterval().getEndMillis() != originGene.getExecutionInterval().getEndMillis()) {
//                    crossoverPoint = i;
//                    break;
//                }
//            }
//
//            if(crossoverPoint > -1) {
//                crossoverFound = true;
//                for (int i = 0; i < crossoverPoint; i++) {
//                    Chromosome.Gene newGene = new_chromosome_1_genes.get(i);
//                    Chromosome.Gene originGene = origin_chromosome_1_genes.get(i);
//                    assertTrue(newGene.getProcessStepSchedulingUnit().getUid().equals(originGene.getProcessStepSchedulingUnit().getUid()) &&
//                            newGene.getExecutionInterval().getStartMillis() == originGene.getExecutionInterval().getStartMillis() &&
//                            newGene.getExecutionInterval().getEndMillis() == originGene.getExecutionInterval().getEndMillis());
//                }
//                for (int i = crossoverPoint; i < new_chromosome_1_genes.size(); i++) {
//                    Chromosome.Gene newGene = new_chromosome_1_genes.get(i);
//                    Chromosome.Gene originGene = origin_chromosome_2_genes.get(i);
//                    assertTrue(newGene.getProcessStepSchedulingUnit().getUid().equals(originGene.getProcessStepSchedulingUnit().getUid()) &&
//                            newGene.getExecutionInterval().getStartMillis() == originGene.getExecutionInterval().getStartMillis() &&
//                            newGene.getExecutionInterval().getEndMillis() == originGene.getExecutionInterval().getEndMillis());
//                }
//
//                for (int i = 0; i < crossoverPoint; i++) {
//                    Chromosome.Gene newGene = new_chromosome_2_genes.get(i);
//                    Chromosome.Gene originGene = origin_chromosome_2_genes.get(i);
//                    assertTrue(newGene.getProcessStepSchedulingUnit().getUid().equals(originGene.getProcessStepSchedulingUnit().getUid()) &&
//                            newGene.getExecutionInterval().getStartMillis() == originGene.getExecutionInterval().getStartMillis() &&
//                            newGene.getExecutionInterval().getEndMillis() == originGene.getExecutionInterval().getEndMillis());
//                }
//                for (int i = crossoverPoint; i < new_chromosome_1_genes.size(); i++) {
//                    Chromosome.Gene newGene = new_chromosome_2_genes.get(i);
//                    Chromosome.Gene originGene = origin_chromosome_1_genes.get(i);
//                    assertTrue(newGene.getProcessStepSchedulingUnit().getUid().equals(originGene.getProcessStepSchedulingUnit().getUid()) &&
//                            newGene.getExecutionInterval().getStartMillis() == originGene.getExecutionInterval().getStartMillis() &&
//                            newGene.getExecutionInterval().getEndMillis() == originGene.getExecutionInterval().getEndMillis());
//                }
//            }
//        }
//        assertTrue(crossoverFound);
//
//        crossoverChromosomes.forEach(chromosome -> {
//            Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits = chromosome.getFlattenChromosome().stream().map(gene -> gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm()).collect(Collectors.toSet());
//            virtualMachineSchedulingUnits.forEach(unit -> assertTrue(vmSelectionHelper.checkEnoughResourcesLeftOnVM(unit)));
//        });
//
//        crossoverChromosomes.forEach(chromosome -> orderMaintainer.orderIsOk(chromosome.getGenes()));
//
//    }
}