package at.ac.tuwien.infosys.viepepc;

import org.joda.time.Interval;
import org.springframework.boot.SpringApplication;

public class IntervallTest {

    public static void main(String[] args) {

        Interval interval1 = new Interval(100, 200);
        Interval interval2 = new Interval(150, 250);
        Interval interval3 = new Interval( 50, 150);

        Interval overlap1 = interval1.overlap(interval2);
        Interval overlap2 = interval1.overlap(interval3);

        System.out.println("overlap1=" + overlap1.toDuration().getMillis());
        System.out.println("overlap2=" + overlap2.toDuration().getMillis());

    }

}
