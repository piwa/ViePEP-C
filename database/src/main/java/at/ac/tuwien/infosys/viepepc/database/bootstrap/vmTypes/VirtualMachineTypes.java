package at.ac.tuwien.infosys.viepepc.database.bootstrap.vmTypes;

import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VMType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
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
@XmlRootElement(name="VirtualMachineTypes")
@XmlAccessorType(XmlAccessType.FIELD)
public class VirtualMachineTypes {

    @XmlElement(name = "VMType")
    private List<VMType> vmTypes = new ArrayList<>();

}
