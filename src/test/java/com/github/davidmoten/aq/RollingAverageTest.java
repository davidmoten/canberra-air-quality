package com.github.davidmoten.aq;

import static com.github.davidmoten.aq.RollingAverage.WINDOW_LENGTH;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.ejml.simple.SimpleMatrix;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private static final String START_TIMESTAMP = "16/12/2019 00:00:00 AM";
    private static final String FINISH_TIMESTAMP = "01/01/2025 00:00:00 AM";

    @Test
    public void extractRawValuesAndPersist() throws IOException {
        for (String name : STATIONS) {
            Result r = RollingAverage.extractData(name, START_TIMESTAMP, FINISH_TIMESTAMP);
            saveDataForExcel(r.entries(), r.z(), new File("target/" + name + ".csv"));
            saveChart(r.entries(), r.z(), name, "target/" + name + ".png", Optional.of(5000.0));
        }
    }

    @Test
    public void extractRawValuesAndPersist2() throws IOException {
        for (String name : STATIONS) {
            String start = RollingAverage.SDF.format(new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)));
            String finish = RollingAverage.SDF.format(new Date());
            Result r = RollingAverage.extractData(name, start, finish);
            saveChart(r.entries(), r.z(), name, "target/" + name + "2.png", Optional.empty());
        }
    }

    private static void saveDataForExcel(List<Entry> list, SimpleMatrix z, File output) throws FileNotFoundException {
        output.getParentFile().mkdirs();
        try (PrintStream out = new PrintStream(output)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for (int i = 0; i < list.size(); i++) {
                out.println(sdf.format(new Date(list.get(i).time)) + "\t" + z.get(i + WINDOW_LENGTH - 1, 0));
            }
        }
    }

    private static void saveChart(List<Entry> list, SimpleMatrix z, String name, String chartFilename,
            Optional<Double> upperBound) throws IOException {
        DefaultCategoryDataset rawHourlyDataset = new DefaultCategoryDataset();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+11:00"));
        double max = 0;
        for (int i = 0; i < list.size(); i++) {
            double val = z.get(i + WINDOW_LENGTH - 1, 0);
            if (val > max) {
                max = val;
            }
            rawHourlyDataset.addValue(Math.max(0, val), name, sdf.format(new Date(list.get(i).time)));
        }
        DefaultCategoryDataset rollingAverageDataset = new DefaultCategoryDataset();
        for (int i = 0; i < list.size(); i++) {
            rollingAverageDataset.addValue(list.get(i).value.orElse(0.0), name + " 24 hr rolling avg",
                    sdf.format(new Date(list.get(i).time)));
        }
        DefaultCategoryDataset threshold = new DefaultCategoryDataset();
        for (int i = 0; i < list.size(); i++) {
            threshold.addValue(200, "Hazardous Threshold PM 2.5 ug/m3", sdf.format(new Date(list.get(i).time)));
        }
        JFreeChart chart = ChartFactory.createBarChart(name + " hourly raw PM 2.5", "Time", "PM 2.5 Raw",
                rawHourlyDataset);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setDataset(0, rollingAverageDataset);
        plot.setDataset(1, threshold);
        plot.setDataset(2, rawHourlyDataset);
        plot.setRenderer(0, new LineAndShapeRenderer());
        LineAndShapeRenderer thresholdRenderer = new LineAndShapeRenderer();
        plot.setRenderer(1, thresholdRenderer);
        plot.setRenderer(2, new BarRenderer());
        CategoryAxis axis = plot.getDomainAxis();
        axis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        ValueAxis rangeAxis = plot.getRangeAxis();
        rangeAxis.setLowerBound(0);
        rangeAxis.setUpperBound(upperBound.orElse((Math.floor(max / 500) + 1) * 500));
        ChartUtils.saveChartAsPNG(new File(chartFilename), chart, (int) Math.round(list.size() / 0.0395), 1200);
        System.out.println("saved chart as png");

    }

    @Test
    public void testSdf() throws ParseException {
        assertEquals(1576461600000L, RollingAverage.SDF.parse("16/12/2019 01:00:00 PM").getTime());
    }

    public static void main(String[] args) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(10, "Civic", "2019-12-23 01:00");
        dataset.addValue(16, "Civic", "2019-12-23 02:00");
        JFreeChart chart = ChartFactory.createBarChart("Civic" + " hourly PM 2.5", "Time", "PM 2.5 Raw", dataset);
        ChartUtils.saveChartAsPNG(new File("target/chart.png"), chart, 2000, 1200);
        System.out.println("saved chart as png");
    }

}
