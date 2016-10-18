package at.ac.tuwien.infosys.viepepc.registry.impl;

import at.ac.tuwien.infosys.viepepc.database.entities.ServiceType;
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

    private List<ServiceType> ServiceType = new ArrayList<>();

}
