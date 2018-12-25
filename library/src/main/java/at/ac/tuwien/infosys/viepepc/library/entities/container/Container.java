package at.ac.tuwien.infosys.viepepc.library.entities.container;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.persistence.*;
import java.util.UUID;

/**
 */

@Entity
@Table(name = "container")
@Getter
@Setter
@AllArgsConstructor
public class Container implements Cloneable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String containerID;

    @ManyToOne//(cascade = CascadeType.ALL)
    @JoinColumn(name="containerConfigurationId")
    private ContainerConfiguration containerConfiguration;
    @ManyToOne
    @JoinColumn(name="containerImageId")
    private ContainerImage containerImage;

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime startDate;
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private Interval scheduledDeployedInterval;

    private ContainerStatus containerStatus;

    private boolean bareMetal = false;
    private String externPort;
    private String ipAddress;
    private String providerContainerId = "";

    @ManyToOne(cascade = CascadeType.ALL)
    private VirtualMachine virtualMachine;

    public Container() {
        containerID = UUID.randomUUID().toString().substring(0, 8) + "_temp";         // create temp id
    }

    public String getName() {
        return containerConfiguration.getName() + "_" + this.containerImage.getServiceType().getName() + "_" + containerID;
    }

    public void shutdownContainer() {
        if(virtualMachine != null) {
            virtualMachine.undeployContainer(this);
            virtualMachine = null;
        }
        containerStatus = ContainerStatus.TERMINATED;
        bareMetal = false;
    }

    public Container clone(ServiceType serviceType) throws CloneNotSupportedException {
        Container container = (Container)super.clone();
        container.setContainerConfiguration(this.containerConfiguration.clone());
        container.setContainerImage(this.containerImage.clone(serviceType));
        return container;
    }

    @Override
    public String toString() {
        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

        String startString = startDate == null ? "NULL" : startDate.toString();
        String vmString = virtualMachine == null ? "NULL" : virtualMachine.getInstanceId();

        if(isBareMetal()) {
            return "Container{" +
                    "name='" + getName() + '\'' +
                    ", status=" + containerStatus +
                    ", startedAt=" + startString +
                    ", url=" + ipAddress + ":" + externPort +
                    ", serviceType=" + containerImage.getServiceType().getName() +
                    '}';
        }
        else {
            return "Container{" +
                    "name='" + getName() + '\'' +
                    ", startedAt=" + startString +
                    ", runningOnVM=" + vmString +
                    ", externalPort=" + externPort +
                    ", serviceType=" + containerImage.getServiceType().getName() +
                    '}';
        }
    }



    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Container other = (Container) obj;
        if (containerID == null) {
            if (other.containerID != null) {
                return false;
            }
        }
        else if (!containerID.equals(other.containerID)) {
            return false;
        }
        if (containerImage == null) {
            if (other.containerImage != null) {
                return false;
            }
        }
        else if (!containerImage.equals(other.containerImage)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        }
        else if (!id.equals(other.id)) {
            return false;
        }

        //  also consider the name here:
        String otherName = other.getName();
        String thisName = this.getName();
        if (thisName == null) {
            if (otherName != null) {
                return false;
            }
        }
        else if (!thisName.equals(otherName)) {
            return false;
        }
        return true;
    }
}
