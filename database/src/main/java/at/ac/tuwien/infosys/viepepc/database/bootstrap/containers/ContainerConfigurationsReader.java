package at.ac.tuwien.infosys.viepepc.database.bootstrap.containers;

import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Created by philippwaibel on 18/10/2016.
 */
@Component
@Slf4j
public class ContainerConfigurationsReader {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    @Value("${container.configuration.path}")
    private String containerConfigurationPath;

    public void readContainerConfigurations() {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance( ContainerConfigurations.class );
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
//            File file = Paths.get(Objects.requireNonNull(this.getClass().getClassLoader().getResource(containerConfigurationPath)).toURI()).toFile();
            InputStream file = this.getClass().getResourceAsStream(containerConfigurationPath);
            ContainerConfigurations containerConfigurations = (ContainerConfigurations) jaxbUnmarshaller.unmarshal(file);
//            ContainerConfigurations containerConfigurations = (ContainerConfigurations) jaxbUnmarshaller.unmarshal(new StringReader(xmlString));
            inMemoryCache.getContainerConfigurations().addAll(containerConfigurations.getConfiguration());
        } catch (JAXBException | NullPointerException e) {
            log.error("Exception", e);
        }

    }
}
