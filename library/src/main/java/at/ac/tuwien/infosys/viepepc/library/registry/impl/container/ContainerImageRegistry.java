package at.ac.tuwien.infosys.viepepc.library.registry.impl.container;

import at.ac.tuwien.infosys.viepepc.library.entities.container.ContainerImage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by philippwaibel on 18/10/2016.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@XmlRootElement(name="ContainerImages")
public class ContainerImageRegistry {

//    @XmlElement(name = "containerImage")
//    @XmlElementWrapper(name = "containerImages")
    private List<ContainerImage> containerImage = new ArrayList<>();

}
