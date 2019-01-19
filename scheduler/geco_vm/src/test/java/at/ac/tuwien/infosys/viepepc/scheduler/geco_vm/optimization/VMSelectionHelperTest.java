package at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization;

import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceTypeResources;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.GeCoVmApplication;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.configuration.TestSchedulerGecoConfiguration;
import at.ac.tuwien.infosys.viepepc.scheduler.geco_vm.optimization.entities.*;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertFalse;
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
public class VMSelectionHelperTest {
//
//    @Autowired
//    private CacheVirtualMachineService cacheVirtualMachineService;
//    @Autowired
//    private VMSelectionHelper vmSelectionHelper;
//
//    @Value("${only.container.deploy.time}")
//    private long containerDeploymentDuration = 40000;
//    @Value("${virtual.machine.default.deploy.time}")
//    private long virtualMachineDeploymentTime;
//
//    @Before
//    public void initialize() {
////        vmSelectionHelper.initialize();
//    }
//
//    @Test
//    public void checkVmSizeAndSolveSpaceIssues() {
//
//
//    }
//
//    @Test
//    public void distributeContainers() {
////        double containerCores = 0.5;
////        double containerRam = 50;
////        double containerCores_1 = 1;
////        double containerRam_1 = 100;
////        double containerCores_2 = 2;
////        double containerRam_2 = 200;
////        double containerCores_3 = 3;
////        double containerRam_3 = 300;
////
////        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = getVMSchedulingUnitWithSmallContainer(containerCores, containerRam);
////        Set<ContainerSchedulingUnit> vmContainerSchedulingUnit = virtualMachineSchedulingUnit.getScheduledContainers();
////        List<ContainerSchedulingUnit> originalContainerSchedulingUnits = getThreeContainerSchedulingUnits(containerCores_1, containerRam_1, containerCores_2, containerRam_2, containerCores_3, containerRam_3);
////        virtualMachineSchedulingUnit.getScheduledContainers().addAll(originalContainerSchedulingUnits);
////        virtualMachineSchedulingUnit.getScheduledContainers().forEach(containerSchedulingUnit -> containerSchedulingUnit.setScheduledOnVm(virtualMachineSchedulingUnit));
////
////        List<Chromosome.Gene> genes = new ArrayList<>();
////        vmContainerSchedulingUnit.forEach(unit -> genes.addAll(unit.getProcessStepGenes()));
////
////        VirtualMachineSchedulingUnit newVirtualMachineSchedulingUnit = vmSelectionHelper.distributeContainers(virtualMachineSchedulingUnit, originalContainerSchedulingUnits);
////
////        assertThat(newVirtualMachineSchedulingUnit, not(virtualMachineSchedulingUnit));
////
////        Set<ContainerSchedulingUnit> newContainerSchedulingUnits_1 = virtualMachineSchedulingUnit.getScheduledContainers();
////        Set<ContainerSchedulingUnit> newContainerSchedulingUnits_2 = newVirtualMachineSchedulingUnit.getScheduledContainers();
////
////        assertThat(newContainerSchedulingUnits_1.size(), is(2));
////        assertThat(newContainerSchedulingUnits_2.size(), is(2));
////
////        newContainerSchedulingUnits_2.forEach(element -> assertThat(newContainerSchedulingUnits_1, not(hasItems(element))));
////        newContainerSchedulingUnits_1.forEach(element -> assertThat(newContainerSchedulingUnits_2, not(hasItems(element))));
////
////        List<ContainerSchedulingUnit> combinedNewSchedulingUnits = new ArrayList<>(newContainerSchedulingUnits_1);
////        combinedNewSchedulingUnits.addAll(newContainerSchedulingUnits_2);
////        originalContainerSchedulingUnits.forEach(element -> assertThat(combinedNewSchedulingUnits, hasItem(element)));
////        vmContainerSchedulingUnit.forEach(element -> assertThat(combinedNewSchedulingUnits, hasItem(element)));
////
////        newContainerSchedulingUnits_1.forEach(element -> assertThat(element.getScheduledOnVm(), is(virtualMachineSchedulingUnit)));
////        newContainerSchedulingUnits_2.forEach(element -> assertThat(element.getScheduledOnVm(), is(newVirtualMachineSchedulingUnit)));
////
////        assertThat(genes.get(0).getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm(), is(virtualMachineSchedulingUnit));
////        assertThat(genes.get(1).getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm(), is(virtualMachineSchedulingUnit));
////        assertThat(genes.get(2).getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm(), is(newVirtualMachineSchedulingUnit));
////        assertThat(genes.get(3).getProcessStepSchedulingUnit().getContainerSchedulingUnit().getScheduledOnVm(), is(newVirtualMachineSchedulingUnit));
//    }
//
//    @Test
//    public void checkEnoughResourcesLeftOnVM_1() {
//        double containerCores = 0.1;
//        double containerRam = 50;
//        double containerCores_1 = 1;
//        double containerRam_1 = 100;
//
//        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = getVMSchedulingUnitWithSmallContainer(containerCores, containerRam);
//
//        List<ContainerSchedulingUnit> containerSchedulingUnits = new ArrayList<>();
//        containerSchedulingUnits.add(getContainerSchedulingUnitMock(containerCores, containerRam));
//
//        assertTrue(vmSelectionHelper.checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, containerSchedulingUnits));
//
//        containerSchedulingUnits.add(getContainerSchedulingUnitMock(containerCores_1, containerRam_1));
//        assertFalse(vmSelectionHelper.checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, containerSchedulingUnits));
//    }
//
//    @Test
//    public void checkEnoughResourcesLeftOnVM_2() {
//        double containerCores = 0.2;
//        double containerRam = 50;
//
//        DateTime startTime = DateTime.now();
//
//        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = getVMSchedulingUnitWithSmallContainer(containerCores, containerRam);
//        List<ContainerSchedulingUnit> containerSchedulingUnits = new ArrayList<>();
//        containerSchedulingUnits.add(getContainerSchedulingUnitMock(containerCores, containerRam, startTime, new Long(10000)));
//        assertTrue(vmSelectionHelper.checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, containerSchedulingUnits));
//
//        startTime = startTime.plus(10000*2);
//        for(double i = containerCores; i < virtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType().getCores(); i = i + containerCores) {
//            containerSchedulingUnits.add(getContainerSchedulingUnitMock(containerCores, containerRam, startTime, new Long(10000)));
//            assertTrue(vmSelectionHelper.checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, containerSchedulingUnits));
//        }
//
//        containerSchedulingUnits.add(getContainerSchedulingUnitMock(containerCores, containerRam, startTime, new Long(10000)));
//        assertFalse(vmSelectionHelper.checkEnoughResourcesLeftOnVM(virtualMachineSchedulingUnit, containerSchedulingUnits));
//
//    }
//
//    @Test
//    public void resizeVM() {
//
//        double containerCores = 0.5;
//        double containerRam = 50;
//        double containerCores_1 = 1;
//        double containerRam_1 = 100;
//        double containerCores_2 = 2;
//        double containerRam_2 = 200;
//        double containerCores_3 = 3;
//        double containerRam_3 = 300;
//
//        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = getVMSchedulingUnitWithSmallContainer(containerCores, containerRam);
//        VMType originalVmType = virtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType();
//
//        List<ContainerSchedulingUnit> containerSchedulingUnits = getThreeContainerSchedulingUnits(containerCores_1, containerRam_1, containerCores_2, containerRam_2, containerCores_3, containerRam_3);
//
//        try {
//            vmSelectionHelper.resizeVM(virtualMachineSchedulingUnit, containerSchedulingUnits);
//
//
//            VMType newVmType = virtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType();
//
//            assertThat(newVmType, not(originalVmType));
//
//            assertThat(newVmType.getCpuPoints(), Matchers.greaterThan(originalVmType.getCpuPoints()));
//            assertThat(newVmType.getRamPoints(), Matchers.greaterThan(originalVmType.getRamPoints()));
//
//            assertThat(newVmType.getCpuPoints(), greaterThanOrEqualTo(containerCores_1 + containerCores_2 + containerCores_3 + containerCores));
//            assertThat(newVmType.getRamPoints(), greaterThanOrEqualTo(containerRam_1 + containerRam_2 + containerRam_3 + containerRam));
//
//        } catch (VMTypeNotFoundException e) {
//            log.error("Exception", e);
//        }
//    }
//
////    @Test
////    public void getVirtualMachineSchedulingUnit_oneContainer() {
////
////        double containerCores = 1;
////        double containerRam = 1;
////
////        ContainerSchedulingUnit containerSchedulingUnit = getContainerSchedulingUnitMock(containerCores, containerRam);
////
////        List<ContainerSchedulingUnit> containerSchedulingUnits = new ArrayList<>();
////        containerSchedulingUnits.add(containerSchedulingUnit);
////
////        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = vmSelectionHelper.getVirtualMachineSchedulingUnit(containerSchedulingUnits);
////
////        VMType selectedVmType = virtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType();
////
////        assertThat(selectedVmType.getCpuPoints(), greaterThanOrEqualTo(containerCores));
////        assertThat(selectedVmType.getRamPoints(), greaterThanOrEqualTo(containerRam));
////    }
////
////    @Test
////    public void getVirtualMachineSchedulingUnit_threeContainer() {
////
////        double containerCores_1 = 1;
////        double containerRam_1 = 100;
////        double containerCores_2 = 2;
////        double containerRam_2 = 200;
////        double containerCores_3 = 3;
////        double containerRam_3 = 300;
////
////        List<ContainerSchedulingUnit> containerSchedulingUnits = getThreeContainerSchedulingUnits(containerCores_1, containerRam_1, containerCores_2, containerRam_2, containerCores_3, containerRam_3);
////
////        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = vmSelectionHelper.getVirtualMachineSchedulingUnit(containerSchedulingUnits);
////
////        VMType selectedVmType = virtualMachineSchedulingUnit.getVirtualMachineInstance().getVmType();
////
////        assertThat(selectedVmType.getCpuPoints(), greaterThanOrEqualTo(containerCores_1 + containerCores_2 + containerCores_3));
////        assertThat(selectedVmType.getRamPoints(), greaterThanOrEqualTo(containerRam_1 + containerRam_2 + containerRam_3));
////    }
//
//    private ProcessStepSchedulingUnit getProcessStepSchedulingUnitMock() {
//
//        ServiceTypeResources serviceTypeResources = new ServiceTypeResources();
//        serviceTypeResources.setMakeSpan(10000);
//
//        ServiceType serviceType = new ServiceType();
//        serviceType.setServiceTypeResources(serviceTypeResources);
//
//        ProcessStep processStep = new ProcessStep();
//        processStep.setLastElement(false);
//        processStep.setServiceType(serviceType);
//
//        return new ProcessStepSchedulingUnit(processStep);
//    }
//
//    private ContainerSchedulingUnit getContainerSchedulingUnitMock(double cores, double ram) {
//        return getContainerSchedulingUnitMock(cores, ram, DateTime.now(), null);
//    }
//
//    private ContainerSchedulingUnit getContainerSchedulingUnitMock(double cores, double ram, DateTime startTime, Long executionDuration) {
//
//        ProcessStepSchedulingUnit processStepSchedulingUnit = getProcessStepSchedulingUnitMock();
//        if(executionDuration != null) {
//            processStepSchedulingUnit.getProcessStep().getServiceType().getServiceTypeResources().setMakeSpan(executionDuration);
//        }
//        Chromosome.Gene gene = new Chromosome.Gene(processStepSchedulingUnit, startTime, false);
//        Set<Chromosome.Gene> processStepGenes = new HashSet<>();
//        processStepGenes.add(gene);
//
//
//        ContainerSchedulingUnit containerSchedulingUnit = new ContainerSchedulingUnit(containerDeploymentDuration, false);
//
//        ContainerConfiguration containerConfiguration = new ContainerConfiguration(new Long(1), "test", cores, ram, 0);
//        Container container = new Container();
//        container.setContainerConfiguration(containerConfiguration);
//        containerSchedulingUnit.setContainer(container);
//        containerSchedulingUnit.setProcessStepGenes(processStepGenes);
//        processStepSchedulingUnit.setContainerSchedulingUnit(containerSchedulingUnit);
//
//        return containerSchedulingUnit;
//    }
//
//    private VirtualMachineSchedulingUnit getVMSchedulingUnitWithSmallContainer(double containerCores, double containerRam) {
//
//        ContainerSchedulingUnit containerSchedulingUnit = getContainerSchedulingUnitMock(containerCores, containerRam);
//
//        List<VMType> allVMTypes = cacheVirtualMachineService.getVMTypes();
//        allVMTypes.sort(Comparator.comparing(VMType::getCores).thenComparing(VMType::getRamPoints));
//
//        VirtualMachineInstance vm = new VirtualMachineInstance(allVMTypes.get(0));
//
//        VirtualMachineSchedulingUnit virtualMachineSchedulingUnit = new VirtualMachineSchedulingUnit(virtualMachineDeploymentTime, false);
//        virtualMachineSchedulingUnit.setVirtualMachineInstance(vm);
//
//        virtualMachineSchedulingUnit.getScheduledContainers().add(containerSchedulingUnit);
//
//        return virtualMachineSchedulingUnit;
//    }
//
//    @NotNull
//    private List<ContainerSchedulingUnit> getThreeContainerSchedulingUnits(double containerCores_1, double containerRam_1, double containerCores_2, double containerRam_2, double containerCores_3, double containerRam_3) {
//        List<ContainerSchedulingUnit> containerSchedulingUnits = new ArrayList<>();
//        containerSchedulingUnits.add(getContainerSchedulingUnitMock(containerCores_1, containerRam_1));
//        containerSchedulingUnits.add(getContainerSchedulingUnitMock(containerCores_2, containerRam_2));
//        containerSchedulingUnits.add(getContainerSchedulingUnitMock(containerCores_3, containerRam_3));
//        return containerSchedulingUnits;
//    }
}