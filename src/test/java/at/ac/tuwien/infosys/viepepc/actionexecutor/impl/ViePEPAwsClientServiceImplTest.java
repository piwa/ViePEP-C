package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.bootstrap.vmTypes.VmTypesReaderImpl;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VMType;
import at.ac.tuwien.infosys.viepepc.database.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.database.inmemory.services.CacheVirtualMachineService;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StopWatch;

import java.util.concurrent.TimeUnit;

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
@Slf4j
public class ViePEPAwsClientServiceImplTest {


    @Autowired
    private ViePEPAwsClientService viePEPAwsClientService;
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

        VMType vmType = virtualMachineService.getVmTypeFromIdentifier(6);
        VirtualMachine vm = new VirtualMachine("test", vmType);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        vm = viePEPAwsClientService.startVM(vm);
        stopWatch.stop();
        log.info("VM bootup time: " + stopWatch.getTotalTimeMillis());

        TimeUnit.SECONDS.sleep(10);

        assertThat(vm.getIpAddress()).isNotNull().isNotEmpty();
        assertThat(viePEPAwsClientService.checkAvailabilityOfDockerhost(vm)).isTrue();

        boolean success = viePEPAwsClientService.stopVirtualMachine(vm);
        assertThat(success).isTrue();

    }

}