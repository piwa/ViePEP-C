package at.ac.tuwien.infosys.viepepc.database.entities.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by philippwaibel on 19/10/2016.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "service_type_resources")
@XmlRootElement(name="ServiceTypeResources")
@XmlAccessorType(XmlAccessType.FIELD)
public class ServiceTypeResources {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @XmlElement
    private double cpuLoad;
    @XmlElement
    private double memory;
    @XmlElement
    private long makeSpan;

}
