package at.ac.tuwien.infosys.viepepc.reasoner.frincu;

import java.util.Date;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 19/10/2016.
 */
public interface Reasoning {

    Future<Boolean> runReasoning(Date date, boolean autoTerminate) throws InterruptedException ;

    long performOptimisation() throws Exception;

    void stop() ;

    void setNextOptimizeTimeNow();

    void setNextOptimizeTimeAfter(long millis);
}
