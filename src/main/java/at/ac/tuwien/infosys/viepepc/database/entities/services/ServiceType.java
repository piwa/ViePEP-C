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
public class ServiceType implements Cloneable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @XmlElement
    private String name;
    @XmlElement
    private boolean onlyInternal;
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
//
//    public void addMonitoredServiceTypeResourceInformation(ServiceTypeResources serviceTypeResources) {
//        monitoredServiceTypeResources.put(new DateTime(), serviceTypeResources);
//    }
//
//    public static ServiceType fromValue(String serviceTypeName) {
//        ServiceType serviceType1 = new ServiceType();
//        serviceType1.name = serviceTypeName;
//        return serviceType1;
//    }

    @Override
    public String toString() {
        return "ServiceType{" +
                "name='" + name + '\'' +
//                ", onlyInternal=" + onlyInternal +
//                ", serviceTypeResources=" + serviceTypeResources +
//                ", monitoredServiceTypeResources=" + monitoredServiceTypeResources +
                '}';
    }

    @Override
    public ServiceType clone() throws CloneNotSupportedException {
        return (ServiceType)super.clone();
    }
}
