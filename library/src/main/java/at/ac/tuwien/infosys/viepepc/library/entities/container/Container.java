package at.ac.tuwien.infosys.viepepc.library.entities.container;

import at.ac.tuwien.infosys.viepepc.library.entities.services.ServiceType;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachineInstance;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.Columns;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.persistence.*;
import java.util.Objects;
import java.util.UUID;

/**
 *
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

    private final UUID internId = UUID.randomUUID();

    @ManyToOne//(cascade = CascadeType.ALL)
    @JoinColumn(name = "containerConfigurationId")
    private ContainerConfiguration containerConfiguration;
    @ManyToOne
    @JoinColumn(name = "containerImageId")
    private ContainerImage containerImage;

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime startDate;

    @Columns(columns={@Column(name="sCloudUsageStartTime"),@Column(name="sCloudUsageEndTime")})
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInterval")
    private Interval scheduledCloudResourceUsage;
    @Columns(columns={@Column(name="sAvailableStartTime"),@Column(name="sAvailableEndTime")})
    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentInterval")
    private Interval scheduledAvailableInterval;

    private ContainerStatus containerStatus;

    private boolean bareMetal = false;
    private String externPort;
    private String ipAddress;
    private String providerContainerId = "";

    @ManyToOne()
    private VirtualMachineInstance virtualMachineInstance;

    public Container() {
        containerID = UUID.randomUUID().toString().substring(0, 8) + "_temp";         // create temp id
        containerStatus = ContainerStatus.UNUSED;
    }

    public String getName() {
        try {
            return containerConfiguration.getName() + "_" + this.containerImage.getServiceType().getName() + "_" + containerID;
        } catch (Exception ex) {
            return containerID;
        }
    }

    public void shutdownContainer() {
        if (virtualMachineInstance != null) {
            virtualMachineInstance.undeployContainer(this);
//            virtualMachineInstance = null;
        }
        containerStatus = ContainerStatus.TERMINATED;
//        bareMetal = false;
    }

    public Container clone(ServiceType serviceType) throws CloneNotSupportedException {
        Container container = (Container) super.clone();
        container.setContainerConfiguration(this.containerConfiguration.clone());
        container.setContainerImage(this.containerImage.clone(serviceType));
        return container;
    }

    @Override
    public String toString() {

        String virtualMachineInstanceId = "";
        if(virtualMachineInstance != null) virtualMachineInstanceId = virtualMachineInstance.getInternId().toString().substring(0,8);

//        String virtualMachineInstanceId = "";
//        if(virtualMachineInstance != null) virtualMachineInstanceId = virtualMachineInstance.getInternId().toString();

        return "Container{" +
//                "id=" + id +
                "internId=" + internId.toString().substring(0,8) +
//                ", containerID='" + containerID + '\'' +
//                ", containerImage=" + containerImage +
                ", startDate=" + startDate +
                ", scheduledCloudResourceUsage=" + scheduledCloudResourceUsage +
                ", scheduledAvailableInterval=" + scheduledAvailableInterval +
                ", containerStatus=" + containerStatus +
                ", bareMetal=" + bareMetal +
                ", containerConfiguration=" + containerConfiguration +
//                ", externPort='" + externPort + '\'' +
//                ", ipAddress='" + ipAddress + '\'' +
//                ", providerContainerId='" + providerContainerId + '\'' +
                ", virtualMachineInstance=" + virtualMachineInstanceId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Container container = (Container) o;
        return internId.toString().equals(container.internId.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(internId.toString());
    }

    //    @Override
//    public boolean equals(Object obj) {
//        if (this == obj) {
//            return true;
//        }
//        if (obj == null) {
//            return false;
//        }
//        if (getClass() != obj.getClass()) {
//            return false;
//        }
//        Container other = (Container) obj;
//        if (containerID == null) {
//            if (other.containerID != null) {
//                return false;
//            }
//        }
//        else if (!containerID.equals(other.containerID)) {
//            return false;
//        }
//        if (containerImage == null) {
//            if (other.containerImage != null) {
//                return false;
//            }
//        }
//        else if (!containerImage.equals(other.containerImage)) {
//            return false;
//        }
//        if (id == null) {
//            if (other.id != null) {
//                return false;
//            }
//        }
//        else if (!id.equals(other.id)) {
//            return false;
//        }
//
//        //  also consider the name here:
//        String otherName = other.getName();
//        String thisName = this.getName();
//        if (thisName == null) {
//            if (otherName != null) {
//                return false;
//            }
//        }
//        else if (!thisName.equals(otherName)) {
//            return false;
//        }
//        return true;
//    }
}
