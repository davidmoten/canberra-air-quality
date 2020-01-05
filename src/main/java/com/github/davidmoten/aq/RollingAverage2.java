package com.github.davidmoten.aq;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.davidmoten.kool.Stream;
import org.davidmoten.kool.json.Json;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import org.ejml.simple.SimpleMatrix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public final class RollingAverage2 {

    public static final SimpleDateFormat SDF = createSdf();
    public static final int WINDOW_LENGTH = 24;
    
    private static final ObjectMapper MAPPER = createObjectMapper();

    private static SimpleDateFormat createSdf() {
        SimpleDateFormat s = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss aaa");
        s.setTimeZone(TimeZone.getTimeZone("GMT+11:00"));
        return s;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // support mapping to Optional fields
        mapper.registerModule(new Jdk8Module());
        return mapper;
    }

    public static Result extractData(String name, String startTimestamp, String finishTimestamp)
            throws FileNotFoundException, IOException {
        System.out.println(name);
        List<Entry> list = Json //
                .stream(() -> RollingAverage2.class.getResourceAsStream("/air.json")) //
                .withMapper(MAPPER) //
                .arrayNode() //
                .flatMap(node -> node.values(Record.class))
                .println() //
                // only since start time inclusive
                .filter(x -> x.time.getTime() >= SDF.parse(startTimestamp).getTime()) //
                // only before finish time exclusive
                .filter(x -> x.time.getTime() < SDF.parse(finishTimestamp).getTime()) //
                // sort by time
                .sorted((x, y) -> x.time.compareTo(y.time)) //
                // collect
                .toList() //
                // deal with missing entries
                .map(x -> {
                    // trim all missing entries from the start and finish
                    List<Record> w = Stream //
                            .from(x) //
                            .skipUntil(y -> y.value().isPresent()) //
                            .reverse() //
                            .skipUntil(y -> y.value().isPresent()) //
                            .reverse() //
                            .toList() //
                            .get();

                    List<Entry> v = w.stream().map(rec -> new Entry(name, rec.time.getTime(), rec.value()))
                            .collect(Collectors.toList());

                    // fix missing using average where possible or repeating previous value
                    for (int i = 1; i < v.size() - 1; i++) {
                        Entry entry = v.get(i);
                        if (!entry.value.isPresent()) {
                            if (v.get(i + 1).value.isPresent()) {
                                Entry entry2 = new Entry(entry.name, entry.time,
                                        Optional.of((v.get(i - 1).value.get() + v.get(i + 1).value.get()) / 2));
                                v.set(i, entry2);
                            } else {
                                Entry entry2 = new Entry(entry.name, entry.time, Optional.of(v.get(i - 1).value.get()));
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

    private static long parseTime(String s) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-DD'T'hh:mm:ss.SSS");
        try {
            return sdf.parse(s).getTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Json //
                .stream(() -> RollingAverage2.class.getResourceAsStream("/air.json")) //
                .arrayNode() //
                .flatMap(node -> {
                    ArrayNode nd = node.get();
                    String t = nd.get("datetime").asText();
                    double d = nd.get("aqi_pm2_5").asDouble(-1);
                    final Optional<Double> value;
                    if (d == -1) {
                        value = Optional.empty();
                    } else {
                        value = Optional.of(d);
                    }
                    return Stream.of(new Entry("test", parseTime(t), value));
                }).println();
    }
}
