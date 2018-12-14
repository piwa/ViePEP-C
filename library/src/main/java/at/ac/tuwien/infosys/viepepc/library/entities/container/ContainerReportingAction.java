package at.ac.tuwien.infosys.viepepc.library.entities.container;

import at.ac.tuwien.infosys.viepepc.library.entities.Action;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * User: sauron
 * Date: 05.02.14
 * Time: 14:39
 * To change this template use File | Settings | File Templates.
 */
@Entity
@Table(name = "container_reporting_action")
@Getter
@Setter
public class ContainerReportingAction implements Serializable {

    /**
     * database identifier
     * important: this identifier is used to identify a vm in the program
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String containerID;
    private String virtualMachineID;
    private String failureReason = "";

    private String containerConfigurationName;

    @Enumerated(EnumType.STRING)
    private Action dockerAction;

    @Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
    private DateTime timestamp;

    public ContainerReportingAction() {
    }

    public ContainerReportingAction(DateTime date, String containerID, String containerConfigurationName, String vmID, Action action) {
        this.timestamp = date;
        this.containerID = containerID;
        this.containerConfigurationName = containerConfigurationName;
        this.virtualMachineID = vmID;
        this.dockerAction = action;
    }

    public ContainerReportingAction(DateTime date, String containerID, String containerConfigurationName, String vmID, Action action, String failureReason) {
        this.timestamp = date;
        this.containerID = containerID;
        this.containerConfigurationName = containerConfigurationName;
        this.virtualMachineID = vmID;
        this.dockerAction = action;
        this.failureReason = failureReason;
    }


}
