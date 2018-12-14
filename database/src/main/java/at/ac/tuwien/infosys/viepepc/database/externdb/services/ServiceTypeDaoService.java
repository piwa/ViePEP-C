package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ServiceTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 13/06/16. edited by Gerta Sheganaku.
 */
@Component
public class ServiceTypeDaoService {

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    public ServiceType save(ServiceType serviceType) {
        return serviceTypeRepository.save(serviceType);
    }
}
