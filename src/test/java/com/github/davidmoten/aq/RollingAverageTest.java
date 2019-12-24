package com.github.davidmoten.aq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.davidmoten.kool.Stream;
import org.junit.Test;

import Jama.Matrix;

public class RollingAverageTest {

    // Name,GPS,DateTime,NO2,O3_1hr,O3_4hr,CO,PM10,PM2.5,AQI_CO,AQI_NO2,AQI_O3_1hr,AQI_O3_4hr,AQI_PM10,AQI_PM2.5,AQI_Site,Date,Time
    // Florey,"(-35.220606, 149.043539)",16/12/2019 01:00:00
    // PM,0,0.03,0.029,0.32,14.67,3.15,3,0,30,36,29,12,36,16 December 2019,13:00:00

    static final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss aaa");

    @Test
    public void test() throws IOException {
        List<Entry> list = Stream
                .lines(() -> RollingAverage.class.getResourceAsStream("/air.csv"),
                        StandardCharsets.UTF_8) //
                .skip(1) //
                .filter(x -> x.length() > 0) //
                .map(x -> x.replaceAll("\".*\",", "")) //
                .map(x -> x.split(",")) //
                .filter(x -> x[12].length() > 0) //
                .filter(x -> x[1].length() > 0) //
                .map(x -> new Entry(x[0], toTime(x[1]), Double.parseDouble(x[12]))) //
                .filter(x -> x.time > sdf.parse("01/11/2019 01:00:00 AM").getTime()) //
                .filter(x -> x.name.equalsIgnoreCase("Civic")) //
                .sorted((x, y) -> Long.compare(x.time, y.time)) //
                .takeLast(10) //
                .toList() //
                .get();
        int windowLength = 4;
        System.out.println(list.size());
        Matrix m = new Matrix(list.size() - windowLength + 1, list.size());
        for (int row = 0; row < m.getRowDimension(); row++) {
            for (int col = row; col < row + windowLength; col++) {
                m.set(row, col, 1 / (double) windowLength);
            }
        }
        System.out.println(toString(m));
        Matrix inv = m.inverse();
        System.out.println(inv.getRowDimension() + "x" + inv.getColumnDimension());
    }

    private static String toString(Matrix m) {
        StringBuilder b = new StringBuilder();
        for (int row = 0; row < m.getRowDimension(); row++) {
            for (int col = 0; col < m.getColumnDimension(); col++) {
                if (col > 0) {
                    b.append("\t");
                }
                b.append(m.get(row, col));
            }
            b.append("\n");
        }
        return b.toString();
    }

    @Test
    public void testSdf() throws ParseException {
        sdf.parse("16/12/2019 01:00:00 PM");
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
        final double value;

        Entry(String name, long time, double value) {
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
