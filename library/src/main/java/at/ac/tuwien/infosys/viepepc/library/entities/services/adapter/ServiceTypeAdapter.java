package at.ac.tuwien.infosys.viepepc.library.entities.services.adapter;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;

import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * Created by philippwaibel on 07/12/2016.
 */
public class ServiceTypeAdapter extends XmlAdapter<String, ServiceType> {
    @Override
    public ServiceType unmarshal(String value) {
        ServiceType serviceType = new ServiceType();
        serviceType.setName(value);
        return serviceType;
    }

    @Override
    public String marshal(ServiceType serviceType) {
        return serviceType.getName();
    }
}
