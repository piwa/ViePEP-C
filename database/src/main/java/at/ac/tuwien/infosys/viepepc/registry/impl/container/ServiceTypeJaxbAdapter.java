package at.ac.tuwien.infosys.viepepc.registry.impl.container;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.registry.ServiceRegistryReader;
import at.ac.tuwien.infosys.viepepc.registry.impl.service.ServiceRegistryReaderImpl;

import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * Created by philippwaibel on 04/04/2017.
 */
public class ServiceTypeJaxbAdapter extends XmlAdapter<String, ServiceType> {

    private ServiceRegistryReader serviceRegistryReader = ServiceRegistryReaderImpl.getInstance();

    @Override
    public ServiceType unmarshal(String serviceTypeName) throws Exception {
        ServiceType serviceType = serviceRegistryReader.findServiceType(serviceTypeName);
        return serviceType;
    }

    @Override
    public String marshal(ServiceType v) throws Exception {
        return null;
    }
}
