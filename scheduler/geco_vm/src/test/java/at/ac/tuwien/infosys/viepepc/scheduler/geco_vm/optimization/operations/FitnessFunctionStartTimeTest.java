package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.operations;

import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.GeCoVmApplication;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.TestSchedulerGecoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

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
public class FitnessFunctionStartTimeTest {
//
//    @Autowired
//    private DeadlineAwareFactory deadlineAwareFactory;
//    @Autowired
//    private WorkflowGenerationHelper workflowGenerationHelper;
//    @Autowired
//    private CacheWorkflowService cacheWorkflowService;
//    @Autowired
//    private CacheVirtualMachineService cacheVirtualMachineService;
//    @Autowired
//    private FitnessFunction fitnessFunction;
//
//    @Value("${max.optimization.duration}")
//    private long maxOptimizationDuration = 60000;
//    @Value("${additional.optimization.time}")
//    private long additionalOptimizationTime = 5000;
//    @Value("${fitness.cost.cpu}")
//    private double cpuCost = 14;
//    @Value("${fitness.cost.ram}")
//    private double ramCost = 3;
//    @Value("${fitness.leasing.cost.factor}")
//    private double leasingCostFactor = 10;
//    @Value("${fitness.penalty.time.factor}")
//    private double penaltyTimeFactor = 0.001;
//
//    private DateTime optimizationEndTime;
//
//    @Before
//    public void initialize() {
//        this.optimizationEndTime = DateTime.now().plus(this.maxOptimizationDuration).plus(this.additionalOptimizationTime);
//        this.fitnessFunction.setOptimizationEndTime(this.optimizationEndTime);
//        this.cacheVirtualMachineService.getAllVMInstancesFromInMemory().clear();
//    }
//
//    @Test
//    public void getFitness() throws Exception {
//
//        deadlineAwareFactory.initialize(workflowGenerationHelper.createSequentialProcess(), optimizationEndTime);
//        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());
//
//        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
//        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnitSet = new HashSet<>();
//        for (Chromosome.Gene gene : genes) {
//            VirtualMachineInstance vm = cacheVirtualMachineService.getNewVirtualMachineInstance(2);
//            gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachineInstance().setVmType(vm.getVmType());
//            virtualMachineSchedulingUnitSet.add(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm());
//        }
//
//        assertEquals(calculateLeasingCost(virtualMachineSchedulingUnitSet), fitnessFunction.getFitness(chromosome, null),0.0);
//        assertEquals(0.0, fitnessFunction.getPenaltyCost(), 0.0);
//        assertEquals(calculateLeasingCost(virtualMachineSchedulingUnitSet), fitnessFunction.getLeasingCost(), 0.0);
//    }
//
//    @Test
//    public void getFitnessWithUnusedVMs() throws Exception {
//
//        deadlineAwareFactory.initialize(workflowGenerationHelper.createSequentialProcess(), optimizationEndTime);
//        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());
//        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
//
//        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnitSet = new HashSet<>();
//        for (Chromosome.Gene gene : genes) {
//            VirtualMachineInstance vm = cacheVirtualMachineService.getNewVirtualMachineInstance(2);
//            gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachineInstance().setVmType(vm.getVmType());
//            virtualMachineSchedulingUnitSet.add(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm());
//        }
//
//        VirtualMachineInstance virtualMachineInstance = cacheVirtualMachineService.getNewVirtualMachineInstance(2);
//        virtualMachineInstance.setDeploymentStartTime(this.optimizationEndTime.minusMinutes(10));
//        virtualMachineInstance.setVirtualMachineStatus(VirtualMachineStatus.DEPLOYED);
//        cacheVirtualMachineService.getAllVMInstancesFromInMemory().add(virtualMachineInstance);
//        Duration unusedVMDuration = new Duration(this.optimizationEndTime.minusMinutes(10), this.optimizationEndTime);
//
//        double leasingCost = calculateLeasingCost(virtualMachineSchedulingUnitSet) + calculateLeasingCostOneVM(virtualMachineInstance, unusedVMDuration);
//
//        assertEquals(leasingCost, fitnessFunction.getFitness(chromosome, null),0.0);
//        assertEquals(leasingCost, fitnessFunction.getLeasingCost(), 0.0);
//        assertEquals(0.0, fitnessFunction.getPenaltyCost(), 0.0);
//    }
//
//    @Test
//    public void getFitnessWithPenalty() throws Exception {
//
//        List<WorkflowElement> workflowElements = workflowGenerationHelper.createSequentialProcess();
//        WorkflowElement process = workflowElements.get(0);
//        cacheWorkflowService.addWorkflowInstance(process);
//        deadlineAwareFactory.initialize(workflowGenerationHelper.createSequentialProcess(), optimizationEndTime);
//        Chromosome chromosome = deadlineAwareFactory.generateRandomCandidate(new Random());
//
//        List<Chromosome.Gene> genes = chromosome.getGenes().stream().flatMap(List::stream).collect(Collectors.toList());
//
//        Chromosome.Gene gene3 = genes.get(2);
//        DateTime processDeadline = process.getDeadlineDateTime();
//        gene3.setExecutionInterval(gene3.getExecutionInterval().withEnd(processDeadline.plusMinutes(10)));
//        Duration penaltyDuration = new Duration(processDeadline, gene3.getExecutionInterval().getEnd());
//        double penaltyCost = process.getPenalty() * penaltyDuration.getMillis() * penaltyTimeFactor;
//
//        Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnitSet = new HashSet<>();
//        for (Chromosome.Gene gene : genes) {
//            VirtualMachineInstance vm = cacheVirtualMachineService.getNewVirtualMachineInstance(2);
//            gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm().getVirtualMachineInstance().setVmType(vm.getVmType());
//            virtualMachineSchedulingUnitSet.add(gene.getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm());
//        }
//
//        assertEquals(calculateLeasingCost(virtualMachineSchedulingUnitSet) + penaltyCost, fitnessFunction.getFitness(chromosome, null),0.0);
//        assertEquals(penaltyCost, fitnessFunction.getPenaltyCost(), 0.0);
//        assertEquals(calculateLeasingCost(virtualMachineSchedulingUnitSet), fitnessFunction.getLeasingCost(), 0.0);
//    }
//
//    private double calculateLeasingCost(Set<VirtualMachineSchedulingUnit> virtualMachineSchedulingUnits) {
//        double leasingCost = 0.0;
//        for (VirtualMachineSchedulingUnit virtualMachineSchedulingUnit : virtualMachineSchedulingUnits) {
//            Duration cloudResourceUsageDuration = new Duration(virtualMachineSchedulingUnit.getCloudResourceUsageInterval());
//            leasingCost = leasingCost + calculateLeasingCostOneVM(virtualMachineSchedulingUnit.getVirtualMachineInstance(), cloudResourceUsageDuration);
//        }
//        return leasingCost;
//    }
//
//    private double calculateLeasingCostOneVM(VirtualMachineInstance virtualMachineInstance, Duration cloudResourceUsageDuration) {
//        VMType vmType = virtualMachineInstance.getVmType();
//        return (vmType.getCores() * cpuCost * cloudResourceUsageDuration.getStandardSeconds() + vmType.getRamPoints() / 1000 * ramCost * cloudResourceUsageDuration.getStandardSeconds()) * leasingCostFactor;
//    }
}