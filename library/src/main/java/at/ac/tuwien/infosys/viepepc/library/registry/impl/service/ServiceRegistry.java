package at.ac.tuwien.infosys.viepepc.library.registry.impl.service;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by philippwaibel on 18/10/2016.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@XmlRootElement(name="ServiceRegistry")
public class ServiceRegistry {

    private List<ServiceType> serviceType = new ArrayList<>();

}
