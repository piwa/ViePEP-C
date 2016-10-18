package at.ac.tuwien.infosys.viepepc.bootstrap.containers;

import at.ac.tuwien.infosys.viepepc.database.inmemory.database.InMemoryCacheImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

/**
 * Created by philippwaibel on 18/10/2016.
 */
@Component
public class ContainerConfigurationsReaderImpl {

    @Autowired
    private InMemoryCacheImpl inMemoryCache;

    public ContainerConfigurationsReaderImpl(@Value("${container.configuration.path}") String containerConfigurationPath) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance( ContainerConfigurations.class );
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            File file = Paths.get(ClassLoader.getSystemResource(containerConfigurationPath).toURI()).toFile();
            ContainerConfigurations containerConfigurations = (ContainerConfigurations) jaxbUnmarshaller.unmarshal(file);
            inMemoryCache.getContainerConfigurations().addAll(containerConfigurations.getConfiguration());
        } catch (JAXBException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

    }
}
