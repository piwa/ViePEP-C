package at.ac.tuwien.infosys.viepepc.library;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Message implements Serializable {

    private String processStepName;
    private ServiceExecutionStatus status;
    private String body;


    @Override
    public String toString() {
        return "Message{" +
                "processStepName=" + processStepName +
                ", status=" + status +
                ", body='" + body + '\'' +
                '}';
    }
}
