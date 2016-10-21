package at.ac.tuwien.infosys.viepepc.database.entities.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.xml.bind.annotation.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by philippwaibel on 18/10/2016.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ServiceType")
@XmlRootElement(name="ServiceType")
@XmlAccessorType(XmlAccessType.FIELD)
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @XmlElement
    private String name;
    @XmlElement
    private double dataToTransfer;
    @XmlElement
    private boolean onlyInternal;
    @XmlElement
    private Integer internPort;
    @XmlElement
    @OneToOne
    private ServiceTypeResources serviceTypeResources;

    @ElementCollection
    @MapKeyColumn(name="time")
    @Column(name="value")
    @CollectionTable(name="monitored_resource_usages", joinColumns=@JoinColumn(name="monitored_resource_usage"))
    private Map<Date, ServiceTypeResources> monitoredServiceTypeResources = new HashMap<>();

    public void addMonitoredServiceTypeResourceInformation(ServiceTypeResources serviceTypeResources) {
        monitoredServiceTypeResources.put(new Date(), serviceTypeResources);
    }


}
