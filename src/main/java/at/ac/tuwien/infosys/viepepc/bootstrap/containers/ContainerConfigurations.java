package at.ac.tuwien.infosys.viepepc.bootstrap.containers;

import at.ac.tuwien.infosys.viepepc.database.entities.container.ContainerConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.xml.bind.annotation.XmlElement;
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
@XmlRootElement(name="ContainerConfigurations")
public class ContainerConfigurations {

    @XmlElement(name = "Configuration")
    private List<ContainerConfiguration> configuration = new ArrayList<>();

}
