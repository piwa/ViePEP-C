package at.ac.tuwien.infosys.viepepc.actionexecutor.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.Container;
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
        "classpath:application-heuristic.properties",
        "classpath:application-container.properties"})
@ActiveProfiles("test")
@Slf4j
public class ViePEPAzureContainerServiceImplTest {

    @Autowired
    private ViePEPAzureContainerServiceImpl azureContainerService;

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {



    }

    @Test
    public void startAndStopAContainerOnFargate_success() throws Exception {
        Container container = new Container();
        container = azureContainerService.startContainer(container);
        azureContainerService.removeContainer(container);
    }



}