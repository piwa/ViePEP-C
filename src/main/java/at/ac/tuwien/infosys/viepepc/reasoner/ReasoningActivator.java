package at.ac.tuwien.infosys.viepepc.reasoner;

import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 13/06/16.
 */
public interface ReasoningActivator {
    void initialize();

    Future<Boolean> start() throws Exception;

    void stop();
}
