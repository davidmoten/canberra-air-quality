package com.github.davidmoten.aq;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.davidmoten.kool.Stream;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

/**
 * Example input lines downloaded from ACT gov portal
 * 
 * <pre>
* Name,GPS,DateTime,NO2,O3_1hr,O3_4hr,CO,PM10,PM2.5,AQI_CO,AQI_NO2,AQI_O3_1hr,AQI_O3_4hr,AQI_PM10,AQI_PM2.5,AQI_Site,Date,Time
* Florey,"(-35.220606, 149.043539)",16/12/2019 01:00:00 PM,0,0.03,0.029,0.32,14.67,3.15,3,0,30,36,29,12,36,16 December 2019,13:00:00
 * </pre>
 **/
public class RollingAverageTest {

    private static final String[] STATIONS = { "Civic", "Florey", "Monash" };
    private static final String START_TIMESTAMP = "01/11/2019 01:00:00 AM";
    static final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss aaa");

    @Test
    public void extractRawValuesAndPersist() throws IOException {
        for (String name : STATIONS) {
            System.out.println(name);
            List<Entry> list = Stream
                    .lines(() -> RollingAverage.class.getResourceAsStream("/air.csv"),
                            StandardCharsets.UTF_8) //
                    // skip header line
                    .skip(1) //
                    // skip blank lines
                    .filter(x -> x.length() > 0) //
                    // remove the quoted geolocation
                    .map(x -> x.replaceAll("\".*\",", "")) //
                    // get items in row
                    .map(x -> x.split(",")) //
                    // ignore if no timestamp present
                    .filter(x -> x[1].length() > 0) //
                    // parse the time and the PM2.5 value
                    .map(x -> new Entry(x[0], toTime(x[1]), getDouble(x[12]))) //
                    // only since start time
                    .filter(x -> x.time > sdf.parse(START_TIMESTAMP).getTime()) //
                    // just the selected station
                    .filter(x -> x.name.equalsIgnoreCase(name)) //
                    // sort by time
                    .sorted((x, y) -> Long.compare(x.time, y.time)) //
                    // collect
                    .toList() //
                    // deal with missing entries
                    .map(x -> {
                        // trim all missing entries from the start and finish
                        List<Entry> v = Stream //
                                .from(x) //
                                .skipUntil(y -> y.value.isPresent()) //
                                .reverse() //
                                .skipUntil(y -> y.value.isPresent()) //
                                .reverse() //
                                .toList() //
                                .get();

                        // fix missing using average where possible or repeating previous value
                        for (int i = 1; i < v.size() - 1; i++) {
                            Entry entry = v.get(i);
                            if (!entry.value.isPresent()) {
                                if (v.get(i + 1).value.isPresent()) {
                                    Entry entry2 = new Entry(entry.name, entry.time, Optional.of(
                                            (v.get(i - 1).value.get() + v.get(i + 1).value.get())
                                                    / 2));
                                    v.set(i, entry2);
                                } else {
                                    Entry entry2 = new Entry(entry.name, entry.time,
                                            Optional.of(v.get(i - 1).value.get()));
                                    v.set(i, entry2);
                                }
                            }
                        }

                        // any missing entries set them to the average of the value before and after
                        return v;
                    }).get();

            int windowLength = 24;

            // Solve Ax = y for x
            
            // calculate the pseudo inverse of A
            SimpleMatrix aInv = getPseudoInverse(list, windowLength);
            
            SimpleMatrix y = new SimpleMatrix(list.size(), 1);
            for (int i = 0; i < list.size(); i++) {
                y.set(i, 0, list.get(i).value.get());
            }
            
            // Then Ainv . A x = Ainv . y
            // Therefore x = Ainv . y
            SimpleMatrix z = aInv.mult(y);

            File outfile = new File("src/output/" + name + ".csv");
            outfile.getParentFile().mkdirs();
            try (PrintStream out = new PrintStream(outfile)) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                for (int i = 0; i < list.size(); i++) {
                    out.println(sdf.format(new Date(list.get(i).time)) + "\t"
                            + z.get(i + windowLength - 1, 0));
                }
            }
        }
    }

    private static SimpleMatrix getPseudoInverse(List<Entry> list, int windowLength) {
        LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.pseudoInverse(true);
        DMatrixRMaj m = new DMatrixRMaj(list.size(), list.size() + windowLength - 1);
        for (int row = 0; row < m.numRows; row++) {
            for (int col = row; col < row + windowLength; col++) {
                m.set(row, col, 1 / (double) windowLength);
            }
        }
        solver.setA(m);

        // destination matrix for the inversion
        DMatrixRMaj inv = new DMatrixRMaj(list.size(), list.size() + windowLength - 1);
        solver.invert(inv);

        return new SimpleMatrix(inv);
    }

    @Test
    public void testSdf() throws ParseException {
        assertEquals(1576461600000L, sdf.parse("16/12/2019 01:00:00 PM").getTime());
    }

    private static Optional<Double> getDouble(String s) {
        try {
            return Optional.of(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static long toTime(String s) {
        try {
            return sdf.parse(s).getTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    static final class Entry {
        final String name;
        final long time;
        final Optional<Double> value;

        Entry(String name, long time, Optional<Double> value) {
            this.name = name;
            this.time = time;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Entry [name=" + name + ", time=" + new Date(time) + ", value=" + value + "]";
        }

    }

}
