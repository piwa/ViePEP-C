package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.database.TestDatabaseConfiguration;
import at.ac.tuwien.infosys.viepepc.database.ViePEPCDatabaseApplication;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerConfiguration;
import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.registry.ContainerImageRegistryReader;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.*;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ViePEPCDatabaseApplication.class, TestDatabaseConfiguration.class},
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
                "container.configuration.path=container-config/containerconfigurations.xml",
                "vm.types.path=cloud-config/vmtypesconfigurations.xml",
                "container.images.path=container-config/containerimages.xml",
                "slack.webhook="
        })
@ActiveProfiles({"test", "GeCo_VM", "VmAndContainer"})
public class WorkflowDaoServiceTest {

    @Autowired
    private CacheVirtualMachineService cacheVirtualMachineService;
    @Autowired
    private WorkflowDaoService workflowDaoService;

    @Test
    public void saveToDatabase() {

        ServiceType serviceType1 = new ServiceType();
        serviceType1.setName("Service1");
        ServiceType serviceType2 = new ServiceType();
        serviceType2.setName("Service2");
        ServiceType serviceType3 = new ServiceType();
        serviceType3.setName("Service3");
        ServiceType serviceType4 = new ServiceType();
        serviceType4.setName("Service4");
        ContainerImage containerImage1 = new ContainerImage("testRepoName","testImageName", serviceType1);
        ContainerImage containerImage2 = new ContainerImage("testRepoName","testImageName", serviceType2);
        ContainerImage containerImage3 = new ContainerImage("testRepoName","testImageName", serviceType3);
        ContainerImage containerImage4 = new ContainerImage("testRepoName","testImageName", serviceType4);

        VMType vmType1 = new VMType();
        vmType1.setFlavorName("flavor1");
        vmType1.setCores(1);
        vmType1.setRamPoints(1);
        vmType1.setStorage(1);
        vmType1.setCosts(1);
        vmType1.setLocation("location1");
        vmType1.setName("vmType1");
        vmType1.setIdentifier(1L);

        VMType vmType2 = new VMType();
        vmType2.setFlavorName("flavor2");
        vmType2.setCores(2);
        vmType2.setRamPoints(2);
        vmType2.setStorage(2);
        vmType2.setCosts(2);
        vmType2.setLocation("location2");
        vmType2.setName("vmType2");
        vmType2.setIdentifier(2L);

        VirtualMachineInstance vm1 = new VirtualMachineInstance(vmType1);
        VirtualMachineInstance vm2 = new VirtualMachineInstance(vmType1);
        VirtualMachineInstance vm3 = new VirtualMachineInstance(vmType2);

        Container container1 = new Container();
        Container container2 = new Container();
        Container container3 = new Container();
        Container container4 = new Container();

        container1.setVirtualMachineInstance(vm1);
        container2.setVirtualMachineInstance(vm1);
        container3.setVirtualMachineInstance(vm2);
        container4.setVirtualMachineInstance(vm3);

        container1.setContainerConfiguration(getContainerConfiguration("container1Config"));
        container2.setContainerConfiguration(getContainerConfiguration("container2Config"));
        container3.setContainerConfiguration(getContainerConfiguration("container3Config"));
        container4.setContainerConfiguration(getContainerConfiguration("container4Config"));

        container1.setContainerImage(containerImage1);
        container2.setContainerImage(containerImage2);
        container3.setContainerImage(containerImage3);
        container4.setContainerImage(containerImage4);

        ProcessStep processStep1 = new ProcessStep("p1", serviceType1, "p1");
        ProcessStep processStep2 = new ProcessStep("p1", serviceType2, "p1");
        ProcessStep processStep3 = new ProcessStep("p1", serviceType3, "p1");
        ProcessStep processStep4 = new ProcessStep("p1", serviceType4, "p1");

        processStep1.setContainer(container1);
        processStep2.setContainer(container2);
        processStep3.setContainer(container3);
        processStep4.setContainer(container4);

        workflowDaoService.saveToDatabase(processStep1);
        workflowDaoService.saveToDatabase(processStep2);
        workflowDaoService.saveToDatabase(processStep3);
        workflowDaoService.saveToDatabase(processStep4);

    }

    private ContainerConfiguration getContainerConfiguration(String name) {
        ContainerConfiguration bestContainerConfig = new ContainerConfiguration();
//        bestContainerConfig.setId(0L);
        bestContainerConfig.setName(name);
        bestContainerConfig.setCores(10);
        bestContainerConfig.setRam(10);
        bestContainerConfig.setDisc(100);
        return bestContainerConfig;
    }
}