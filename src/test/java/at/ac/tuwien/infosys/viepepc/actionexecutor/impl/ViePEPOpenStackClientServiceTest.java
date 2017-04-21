package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.bootstrap.vmTypes.VmTypesReaderImpl;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by philippwaibel on 03/04/2017.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = {
        "classpath:container-config/container.properties",
        "classpath:cloud-config/viepep4.0.properties",
        "classpath:database-config/mysql.properties",
        "classpath:application.properties",
        "classpath:application-container.properties"})
@ActiveProfiles("test")
public class ViePEPOpenStackClientServiceTest {


    @Autowired
    private ViePEPOpenStackClientService viePEPOpenStackClientService;
    @Autowired
    private CacheVirtualMachineService virtualMachineService;
    @Autowired
    private VmTypesReaderImpl vmTypesReader;

    @Before
    public void setUp() throws Exception {
        vmTypesReader.readVMTypes();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void startVMAndStopAgain_Success() throws Exception {

        VMType vmType = virtualMachineService.getVmTypeFromIdentifier(3);
        VirtualMachine vm = new VirtualMachine("test", vmType);
        vm = viePEPOpenStackClientService.startVM(vm);

        assertThat(vm.getIpAddress()).isNotNull().isNotEmpty();
        assertThat(viePEPOpenStackClientService.checkAvailabilityOfDockerhost(vm)).isTrue();

        boolean success = viePEPOpenStackClientService.stopVirtualMachine(vm);

        assertThat(success).isTrue();

    }

}