package at.ac.tuwien.infosys.viepepc.database.services;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerImage;
import at.ac.tuwien.infosys.viepepc.database.repositories.ContainerImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by philippwaibel on 13/06/16. Edited by Gerta Sheganaku
 */
@Component
public class ContainerImageDaoService {

    @Autowired
    private ContainerImageRepository containerImageRepository;

    public ContainerImage save(ContainerImage containerImage) {
        return containerImageRepository.save(containerImage);
    }

    public ContainerImage getContainerImage(ContainerImage containerImage) {
    	// full scan should be ok here, as we don't expect to have many images in the DB
        for(ContainerImage img : containerImageRepository.findAll()) {
        	if(img.getServiceName().equals(containerImage.getServiceName())) {
        		return img;
        	}
        }
        return null;
    }
}
