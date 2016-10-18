package at.ac.tuwien.infosys.viepepc.database.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by philippwaibel on 18/10/2016.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@XmlRootElement(name="ServiceType")
public class ServiceType {

    @XmlElement
    private double cpuLoad;
    @XmlElement
    private double memory;
    @XmlElement
    private long makeSpan;
    @XmlElement
    private String name;
    @XmlElement
    private double dataToTransfer;
    @XmlElement
    private boolean onlyInternal;
    @XmlElement
    private Integer internPort;


}
