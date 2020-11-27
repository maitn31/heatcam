package com.example.heatcam.MeasurementApp.Fragments.Result;

import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import com.example.heatcam.MeasurementApp.Fragments.Measurement.MeasurementAccessObject;
import com.example.heatcam.MeasurementApp.Fragments.IntroFragment.IntroFragment;
import com.example.heatcam.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class QR_code_fragment extends Fragment {

    // min and max values for graph's yaxis
    private float YAXIS_MIN = 33f;
    private float YAXIS_MAX = 40f;

    private final int PREV_MEASUREMENT_COLOR = Color.rgb(36, 252, 223);
    private final int USER_MEASUREMENT_COLOR =  Color.rgb(6, 95, 174);//Color.rgb(25, 45, 223);
    private final int HIGH_TEMP_LINE_COLOR = Color.rgb(175, 70, 70);
    private final int HIGH_TEMP_LINE_TEXT_COLOR = Color.rgb(129, 48, 48);
    private final int RISING_TEMP_LINE_COLOR = Color.rgb(235, 235, 91);

    // at which y coordinate to draw the horizontal line to indicate high/rising temp
    private float highTempLineValue = 38.1f;
    private float risingTempLineValue = 37.4f;

    private TextView text, text1, text2;
    private ImageView imgView;
    private long timer;
    private boolean t = true;

    private Timer task_timer;

    private LineChart measuresChart;
    private MeasurementAccessObject measurementAccessObject;
    private JSONArray measurementsJSON;

    private ArrayList<Double> previouslyMeasuredTemps;
    private double userTemp;

    private LineData graphData;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.qr_code_fragment, container, false);



        //moving background
        ConstraintLayout constraintLayout = (ConstraintLayout) view.findViewById(R.id.ConstraintLayout);
        AnimationDrawable animationDrawable = (AnimationDrawable) constraintLayout.getBackground();
        animationDrawable.setEnterFadeDuration(2000);
        animationDrawable.setExitFadeDuration(4000);
        animationDrawable.start();

        text = view.findViewById(R.id.textView);
        text1 = view.findViewById(R.id.textView1);
        text2 = view.findViewById(R.id.textView2);
        text.setText(R.string.qr_instruction);

        measuresChart = view.findViewById(R.id.measuresChart);

        measurementAccessObject = new MeasurementAccessObject();


        // text1.setText(R.string.FeedBack);
        if (getArguments() != null && !getArguments().isEmpty()) {
            System.out.println(getArguments() + " argumentit");
            double temp = (double)getArguments().get("user_temp");
            double avgTemp = (double) getArguments().get("avg_user_temp");
            userTemp = avgTemp;
            //text1.setText("Your temp was: " + temp);
            //text1.append("\nYour avg temp was: " + avgTemp);
            if (37.4 > temp && temp >= 35.5) {
                text1.setText(R.string.msgNormTmprt);

            } else if (temp >= 37.4) {
                text1.setText(R.string.msgHightTmprt);
            } else {
                text1.setText(R.string.msgLowTmprt);
            }

        }
        text2.setText(R.string.title);

        // commented off to test graph from measurement data
        //imgView = view.findViewById(R.id.qr_code);
        //imgView.setImageResource(R.drawable.qr_code);

        previouslyMeasuredTemps = new ArrayList<>();
        getPreviousMeasurements();
        graphData = initChartData();
        // uncomment in the future
        /*
        if (previouslyMeasuredTemps.size() > 0) {
            if (checkForHighTemp()) {
                // display high temp layout
                text1.setText(R.string.msgHightTmprt);
            } else {
                // display normal temp layout
                text1.setText(R.string.msgNormTmprt);
            }
        }
         */
        initChart();


        task_timer = new Timer();
        task_timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Fragment fragment = new IntroFragment();
                getActivity().getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.animator.slide_in_left, R.animator.slide_in_right, 0, 0)
                        .replace(R.id.fragmentCamera, fragment, "measure_start")
                        .commit();
            }
        }, 60000);

        // freezes sometimes when exiting fragment
        /*
        timer = System.currentTimeMillis();
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                while(timer + 90000 > System.currentTimeMillis()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Fragment fragment = new IntroFragment();
                getActivity().getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.animator.slide_in_left, R.animator.slide_in_right, 0, 0)
                        .replace(R.id.fragmentCamera, fragment, "measure_start")
                        .commit();
            }
        }, 5000);
        */
        // Inflate the layout for this fragment
        return view;
    }

    private boolean checkForHighTemp() {
        if (previouslyMeasuredTemps != null && previouslyMeasuredTemps.size() > 0) {
            double avgFromPrevious = previouslyMeasuredTemps.stream()
                    .mapToDouble(v -> v)
                    .average()
                    .getAsDouble();
            highTempLineValue = (float) avgFromPrevious;
        }
        // probably need to change this logic
        return userTemp > highTempLineValue;
    }

    private void getPreviousMeasurements() {
        try {
            measurementsJSON = measurementAccessObject.read(getContext());
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void initChart() {
        XAxis xAxis = measuresChart.getXAxis();
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(10f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setAxisLineWidth(2f);
        xAxis.setGridLineWidth(3f);
        xAxis.setGridColor(Color.WHITE);
        xAxis.setDrawLabels(false);
        xAxis.setLabelCount(10);
        //xAxis.setDrawGridLines(false);

        YAxis yAxis = measuresChart.getAxisLeft();
        yAxis.setAxisMinimum(YAXIS_MIN);
        yAxis.setAxisMaximum(YAXIS_MAX);
        yAxis.setAxisLineWidth(2f);
        yAxis.setGridLineWidth(3f);
        yAxis.setGridColor(Color.WHITE);
        yAxis.setTextSize(20f);
        yAxis.setTextColor(Color.WHITE);
        //yAxis.setDrawLabels(false);
        //yAxis.setLabelCount(3);
        yAxis.setLabelCount((int) YAXIS_MAX);
        yAxis.setGranularityEnabled(true);
        yAxis.setGranularity(1.0f);
        yAxis.setValueFormatter(new YAxisValueFormatter());
        yAxis.setDrawGridLines(false);

        String highTempLineText = (String) getContext().getResources().getText(R.string.high_temp_line_text);
        LimitLine highTempLine = new LimitLine(highTempLineValue);
        highTempLine.setTextColor(HIGH_TEMP_LINE_TEXT_COLOR);
        highTempLine.setTextSize(18f);
        highTempLine.setLineWidth(3f);
        highTempLine.setLineColor(HIGH_TEMP_LINE_COLOR);
        yAxis.addLimitLine(highTempLine);

        LimitLine risingTempLine = new LimitLine(risingTempLineValue);
        risingTempLine.setLineWidth(3f);
        risingTempLine.setLineColor(RISING_TEMP_LINE_COLOR);
        yAxis.addLimitLine(risingTempLine);


        measuresChart.setVisibleXRangeMaximum(10);
       // measuresChart.fitScreen();
        measuresChart.setDrawBorders(true);
        measuresChart.setBorderWidth(3f);
        measuresChart.setBorderColor(Color.WHITE);
        //measuresChart.setDrawGridBackground(true);
        measuresChart.getDescription().setEnabled(false);
        measuresChart.getAxisRight().setEnabled(false);
        measuresChart.getLegend().setTextSize(20f);

        measuresChart.setData(graphData);

        measuresChart.getLegend().setTextColor(Color.WHITE);
        measuresChart.setTouchEnabled(false);
    }

    private LineData initChartData() {
        ArrayList<Entry> measurements = new ArrayList<>();
        ArrayList<Entry> userMeasurement = new ArrayList<>();
        // colors for red green and blue, depending on y axis value for measurement
        // so the graph draws different color circles for measurements
        List<Integer> colors = new ArrayList<>();
        //Drawable testDrawable = new BitmapDrawable(getResources(), BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        int z = 0;
        // we want to get only the last 11 measurements, the last will be the most recent measurement
        // meaning the last is what the user just measured
        if (measurementsJSON.length() > 11) { // if there are more than 11 measurements
            for (int x = measurementsJSON.length() - 11; x < measurementsJSON.length() - 1; x++) {
                try {
                    measurements.add(new Entry(z, (float) measurementsJSON.getJSONObject(x).getDouble("Measured")));
                    previouslyMeasuredTemps.add(measurementsJSON.getJSONObject(x).getDouble("Measured"));

                    // check if temps are lower/higher than what we've set in YAXIS_MIN and MAX
                    // so we can adjust the graphs min and max accordingly
                    if (measurementsJSON.getJSONObject(x).getDouble("Measured") < YAXIS_MIN) {
                        YAXIS_MIN = (float) Math.floor(measurementsJSON.getJSONObject(x).getDouble("Measured"));
                    }
                    if (measurementsJSON.getJSONObject(x).getDouble("Measured") > YAXIS_MAX) {
                        YAXIS_MAX = (float) Math.ceil(measurementsJSON.getJSONObject(x).getDouble("Measured"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                z++;
            }
        } else { // there are less than 11 measurements so we can just loop the json
            for (int x = 0; x < measurementsJSON.length() - 1; x++) {
                try {
                    measurements.add(new Entry(z, (float) measurementsJSON.getJSONObject(x).getDouble("Measured")));

                    // check if temps are lower/higher than what we've set in YAXIS_MIN and MAX
                    // so we can adjust the graphs min and max accordingly
                    if (measurementsJSON.getJSONObject(x).getDouble("Measured") < YAXIS_MIN) {
                        YAXIS_MIN = (float) Math.floor(measurementsJSON.getJSONObject(x).getDouble("Measured"));
                    }
                    if (measurementsJSON.getJSONObject(x).getDouble("Measured") > YAXIS_MAX) {
                        YAXIS_MAX = (float) Math.ceil(measurementsJSON.getJSONObject(x).getDouble("Measured"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                z++;
            }
        }

        // need to add the last measurement to its own arraylist
        // so we can color the last dot in the graph with different color
        // TODO: use userTemp variable from bundle arguments instead of reading JSON
        try {
            userMeasurement.add(new Entry(z, (float) measurementsJSON.getJSONObject(measurementsJSON.length()-1).getDouble("Measured")));

            // check if temps are lower/higher than what we've set in YAXIS_MIN and MAX
            // so we can adjust the graphs min and max accordingly
            if (measurementsJSON.getJSONObject(measurementsJSON.length()-1).getDouble("Measured") < YAXIS_MIN) {
                YAXIS_MIN = (float) Math.floor(measurementsJSON.getJSONObject(measurementsJSON.length()-1).getDouble("Measured"));
            }
            if (measurementsJSON.getJSONObject(measurementsJSON.length()-1).getDouble("Measured") > YAXIS_MAX) {
                YAXIS_MAX = (float) Math.ceil(measurementsJSON.getJSONObject(measurementsJSON.length()-1).getDouble("Measured"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

       // green circle: normal temp, 35.5–37.3°C
       // yellow circle: rising temp, 37.4–38°C
       // red circle: fever, 38.1–43.0°C
        for (Entry e : measurements) {
            if (e.getY() < 43.0f && e.getY() > 38.1f) {
                colors.add(Color.RED);
            } else if (e.getY() < 38.0f && e.getY() > 37.4f) {
                colors.add(Color.YELLOW);
            } else if (e.getY() < 37.3f && e.getY() > 35.5) {
                colors.add(Color.GREEN);
            } else {
                colors.add(Color.BLUE);
            }
        }

        LineDataSet lineDataSet;
        LineDataSet userDataSet;
        if (measuresChart.getData() != null && measuresChart.getData().getDataSetCount() > 0) {
            lineDataSet = (LineDataSet) measuresChart.getData().getDataSetByIndex(0);
            lineDataSet.setValues(measurements);
            userDataSet = (LineDataSet) measuresChart.getData().getDataSetByIndex(0);
            userDataSet.setValues(userMeasurement);
            measuresChart.getData().notifyDataChanged();
            measuresChart.notifyDataSetChanged();
        } else {
            // change style for measurements before the user
            String prevMeasurementLocalized = (String) getContext().getResources().getText(R.string.prev_measurements);
            lineDataSet = new LineDataSet(measurements, "");
            lineDataSet.setCircleColors(colors);
            lineDataSet.setCircleRadius(8f);
            //lineDataSet.setColors(colors);
            //lineDataSet.setCircleColor(PREV_MEASUREMENT_COLOR);
            lineDataSet.setValueTextSize(0f); // to draw only dots on the graph
            lineDataSet.setDrawCircleHole(false);
            lineDataSet.setDrawFilled(false);
            lineDataSet.setDrawValues(false);
            lineDataSet.setFormLineWidth(4f);
            lineDataSet.setFormSize(0f);
            lineDataSet.enableDashedLine(0, 1, 0);

            //change style for the measurement the user got
            String userMeasurementLocalized = (String) getContext().getResources().getText(R.string.user_measurement);
            userDataSet = new LineDataSet(userMeasurement, userMeasurementLocalized);
            userDataSet.setDrawCircleHole(false);
            userDataSet.setValueTextSize(0f); // to draw only dots on the graph
            userDataSet.setColor(USER_MEASUREMENT_COLOR);
            userDataSet.setCircleColor(USER_MEASUREMENT_COLOR);
            userDataSet.setCircleRadius(8f);
            userDataSet.setDrawFilled(false);
            userDataSet.setFormLineWidth(4f);
            userDataSet.setFormSize(20f);
        }
        ArrayList<ILineDataSet> dataSet = new ArrayList<>();
        dataSet.add(lineDataSet);
        dataSet.add(userDataSet);
        LineData data = new LineData(dataSet);
        return data;
    }

    @Override
    public void onPause() {
        if(task_timer != null) {
            task_timer.cancel();
        }
        super.onPause();
    }

    class YAxisValueFormatter extends ValueFormatter {

        // to make the graph's yaxis only show min and max value on the left side
        @Override
        public String getFormattedValue(float value) {
            if (value == YAXIS_MIN || value == YAXIS_MAX) {
                return super.getFormattedValue(value);
            } else {
                return "";
            }
        }
    }
}