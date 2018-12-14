package at.ac.tuwien.infosys.viepepc.library.registry.impl.service;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.registry.ServiceRegistryReader;

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
