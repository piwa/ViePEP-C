package at.ac.tuwien.infosys.viepepc.registry.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.registry.ContainerRegistryReader;
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
public class ContainerRegistryReaderImpl implements ContainerRegistryReader {

    private ContainerImageRegistry containerImageRegistry;

    public ContainerRegistryReaderImpl(@Value("${container.images.path}") String containerImageRegistryPath) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance( ServiceRegistry.class );
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
    public ContainerImage findContainerImage(String serviceTypeName) {

        for(ContainerImage containerImage : containerImageRegistry.getContainerImage()) {
            if(serviceTypeName.equals(containerImage.getServiceType().getName())) {
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
