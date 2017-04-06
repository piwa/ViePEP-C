package at.ac.tuwien.infosys.viepepc.registry.impl.container;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.registry.ContainerImageRegistryReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
            File file = Paths.get(ClassLoader.getSystemResource(containerImageRegistryPath).toURI()).toFile();
            this.containerImageRegistry = (ContainerImageRegistry) jaxbUnmarshaller.unmarshal(file);
        } catch (JAXBException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
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
