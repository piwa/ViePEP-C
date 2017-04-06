package at.ac.tuwien.infosys.viepepc.registry.impl.service;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.registry.ServiceRegistryReader;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
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
public class ServiceRegistryReaderImpl implements ServiceRegistryReader {

    private static ServiceRegistry serviceRegistry;

    @Getter
    private static ServiceRegistryReaderImpl instance;

    public ServiceRegistryReaderImpl(@Value("${service.registry.path}") String serviceRegistryPath) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance( ServiceRegistry.class );
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            File file = Paths.get(ClassLoader.getSystemResource(serviceRegistryPath).toURI()).toFile();
            this.serviceRegistry = (ServiceRegistry) jaxbUnmarshaller.unmarshal(file);
        } catch (JAXBException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    @PostConstruct
    public void setInstance() {
        instance = this;
    }

    @Override
    public ServiceType findServiceType(String serviceTypeName) throws ServiceTypeNotFoundException {

        for(ServiceType serviceType : serviceRegistry.getServiceType()) {
            if(serviceTypeName.equals(serviceType.getName())) {
                return serviceType;
            }
        }
        throw new ServiceTypeNotFoundException();
    }


    @Override
    public int getServiceTypeAmount() {
        return serviceRegistry.getServiceType().size();
    }


}
