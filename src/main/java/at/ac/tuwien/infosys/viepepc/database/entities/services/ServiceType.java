package at.ac.tuwien.infosys.viepepc.database.entities.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by philippwaibel on 18/10/2016.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "service_type")
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
    @XmlElement(name = "serviceTypeResources")
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name="serviceTypeResourcesId")
    private ServiceTypeResources serviceTypeResources;

    @ElementCollection
    @MapKeyColumn(name="time")
    @Column(name="value")
    @CollectionTable(name="monitored_resource_usages", joinColumns=@JoinColumn(name="monitored_resource_usage"))
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private Map<DateTime, ServiceTypeResources> monitoredServiceTypeResources = new HashMap<>();

    public void addMonitoredServiceTypeResourceInformation(ServiceTypeResources serviceTypeResources) {
        monitoredServiceTypeResources.put(new DateTime(), serviceTypeResources);
    }

    public static ServiceType fromValue(String serviceType) {
        ServiceType serviceType1 = new ServiceType();
        serviceType1.name = serviceType;
        return serviceType1;
    }
}
