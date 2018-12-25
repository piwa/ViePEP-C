package at.ac.tuwien.infosys.viepepc.scheduler.core;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CoreApplicationTests {

    @Test
    public void contextLoads() {
        Interval test = new Interval(DateTime.now().getMillis(), null);
        System.out.println(test);
    }

}

