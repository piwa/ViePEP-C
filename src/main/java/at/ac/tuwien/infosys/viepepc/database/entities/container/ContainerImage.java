package at.ac.tuwien.infosys.viepepc.database.entities.container;

import at.ac.tuwien.infosys.viepepc.database.entities.services.ServiceType;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.xml.bind.annotation.*;

/**
 * @author Gerta Sheganaku
 */
@XmlRootElement(name = "ContainerImage")
@XmlAccessorType(XmlAccessType.FIELD)
@Entity
@Table(name = "containerImage")
@Getter
@Setter
public class ContainerImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@XmlTransient
	private Long id;

	@XmlElement
	private String imageName;
	@XmlElement
	private String repoName;
	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name="serviceTypeId")
	@XmlElement
    private ServiceType serviceType;


	public ContainerImage() {
	}

	public ContainerImage(String repoName, String imageName, ServiceType serviceType) {
		this.serviceType = serviceType;
		this.repoName = repoName;
		this.imageName = imageName;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((imageName == null) ? 0 : imageName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ContainerImage other = (ContainerImage) obj;
		if (imageName == null) {
			if (other.imageName != null)
				return false;
		} else if (!imageName.equals(other.imageName))
			return false;
		return true;
	}

}
