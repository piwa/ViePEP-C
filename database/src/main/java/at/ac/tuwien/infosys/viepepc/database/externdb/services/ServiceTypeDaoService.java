package at.ac.tuwien.infosys.viepepc.database.externdb.services;

import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ServiceTypeResourcesRepository;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.database.externdb.repositories.ServiceTypeRepository;
import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceTypeResources;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

/**
 * Created by philippwaibel on 13/06/16. edited by Gerta Sheganaku.
 */
@Component
public class ServiceTypeDaoService {

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;
    @Autowired
    private ServiceTypeResourcesRepository serviceTypeResourcesRepository;

    public ServiceType save(ServiceType serviceType) {

        if(serviceType.getServiceTypeResources().getId() == null) {
            serviceTypeResourcesRepository.save(serviceType.getServiceTypeResources());
        } else {
            Optional<ServiceTypeResources> serviceTypeResourceType = serviceTypeResourcesRepository.findById(serviceType.getServiceTypeResources().getId());
            if (!serviceTypeResourceType.isPresent()) {
                serviceTypeResourcesRepository.save(serviceType.getServiceTypeResources());
            }
        }
        return serviceTypeRepository.save(serviceType);
    }

    public ServiceType get(ServiceType serviceType) {
        if(serviceType.getId() == null) {
            return null;
        }
        return serviceTypeRepository.findById(serviceType.getId()).orElseThrow(EntityNotFoundException::new);
    }
}
