package com.github.davidmoten.aq;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import org.davidmoten.kool.Stream;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import org.ejml.simple.SimpleMatrix;

public final class RollingAverage {

    public static final SimpleDateFormat SDF = createSdf();
    public static final int WINDOW_LENGTH = 24;

    private static SimpleDateFormat createSdf() {
        SimpleDateFormat s = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss aaa");
        s.setTimeZone(TimeZone.getTimeZone("GMT+11:00"));
        return s;
    }

    public static Result extractDataAndChart(String name, String startTimestamp,
            String finishTimestamp, String chartFilename)
            throws FileNotFoundException, IOException {
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
                .map(x -> new Entry(x[0], toTime(x[1]), getDouble(x[6]), getDouble(x[12]))) //
                // only since start time inclusive
                .filter(x -> x.time >= SDF.parse(startTimestamp).getTime()) //
                // only before finish time exclusive
                .filter(x -> x.time < SDF.parse(finishTimestamp).getTime()) //
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
                                Entry entry2 = new Entry(entry.name, entry.time, entry.raw, Optional.of(
                                        (v.get(i - 1).value.get() + v.get(i + 1).value.get()) / 2));
                                v.set(i, entry2);
                            } else {
                                Entry entry2 = new Entry(entry.name, entry.time, entry.raw,
                                        Optional.of(v.get(i - 1).value.get()));
                                v.set(i, entry2);
                            }
                        }
                    }

                    // any missing entries set them to the average of the value before and after
                    return v;
                }).get();

        // Solve Ax = y for x

        // calculate the pseudo inverse of A
        SimpleMatrix aInv = getPseudoInverse(list, WINDOW_LENGTH);

        SimpleMatrix y = new SimpleMatrix(list.size(), 1);
        for (int i = 0; i < list.size(); i++) {
            y.set(i, 0, list.get(i).value.get());
        }

        // Then Ainv . A x = Ainv . y
        // Therefore x = Ainv . y

        SimpleMatrix z = aInv.mult(y);

        return new Result(list, z);

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

    private static Optional<Double> getDouble(String s) {
        try {
            return Optional.of(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static long toTime(String s) {
        try {
            return SDF.parse(s).getTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class Entry {
        public final String name;
        public final long time;
        public final Optional<Double> raw;
        public final Optional<Double> value;

        public Entry(String name, long time, Optional<Double> raw, Optional<Double> value) {
            this.name = name;
            this.time = time;
            this.raw = raw;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Entry [name=" + name + ", time=" + new Date(time) + ", value=" + value + "]";
        }

    }

    public static final class Result {
        private final List<Entry> list;
        private final SimpleMatrix z;

        public Result(List<Entry> list, SimpleMatrix z) {
            this.list = list;
            this.z = z;
        }

        public List<Entry> entries() {
            return list;
        }

        public SimpleMatrix z() {
            return z;
        }
    }
}
