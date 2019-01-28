package at.ac.tuwien.infosys.viepepc.library.registry.impl.container;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.registry.ContainerImageRegistryReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
@DependsOn("serviceRegistryReaderImpl")
public class ContainerImageRegistryReaderImpl implements ContainerImageRegistryReader {

    private ContainerImageRegistry containerImageRegistry;

    @Value("${container.images.path}")
    private String containerImageRegistryPath;

    @PostConstruct
    public void setContainerImageRegistry() {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance( ContainerImageRegistry.class );
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
//            File file = Paths.get(Objects.requireNonNull(this.getClass().getClassLoader().getResource(containerImageRegistryPath)).toURI()).toFile();
            InputStream file = this.getClass().getResourceAsStream(containerImageRegistryPath);
            this.containerImageRegistry = (ContainerImageRegistry) jaxbUnmarshaller.unmarshal(file);
//            this.containerImageRegistry = (ContainerImageRegistry) jaxbUnmarshaller.unmarshal(new StringReader(xmlString));
        } catch (JAXBException | NullPointerException e) {
            log.error("Exception", e);
        }

    }

    @Override
    public ContainerImage findContainerImage(ServiceType serviceType) throws ContainerImageNotFoundException {

        for(ContainerImage containerImage : containerImageRegistry.getContainerImage()) {
            if(serviceType.getName().equals(containerImage.getServiceType().getName())) {
                return containerImage;
            }
        }

        return null;
    }

    @Override
    public int getContainerImageAmount() {
        return containerImageRegistry.getContainerImage().size();
    }



}
