package com.example.geminitest;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private EditText userInput;
    private ScrollView scrollView;
    private LinearLayout messageContainer;
    private LineChart lineChart;

    private static final String API_KEY = "AIzaSyDNuXZyd6dpGVDlpSGiolbCcxnhC5TP7V4"; // Replace with your Gemini API key
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userInput = findViewById(R.id.user_input);
        scrollView = findViewById(R.id.scrollView);
        messageContainer = findViewById(R.id.message_container);
        lineChart = findViewById(R.id.line_chart);
        ImageButton sendBtn = findViewById(R.id.send_button);

        sendBtn.setOnClickListener(v -> {
            String prompt = userInput.getText().toString().trim();
            if (!prompt.isEmpty()) {
                addMessage(prompt, true);
                sendPromptToGemini(prompt);
                userInput.setText("");
            }
        });
    }

    private void addMessage(String text, boolean isUser) {
        View messageView = getLayoutInflater().inflate(R.layout.message_item, null);
        TextView messageText = messageView.findViewById(R.id.message_text);
        ImageView icon = messageView.findViewById(R.id.icon);

        messageText.setText(text);
        icon.setImageResource(isUser ? R.drawable.ic_user : R.drawable.ic_gemini);

        messageContainer.addView(messageView);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void sendPromptToGemini(String prompt) {
        OkHttpClient client = new OkHttpClient();
        JSONObject json = new JSONObject();

        try {
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            json.put("contents", contents);
        } catch (JSONException e) {
            Log.e("JSON_ERROR", "Error creating JSON request", e);
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder().url(API_URL).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> addMessage("❌ Error: " + e.getMessage(), false));
            }

            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String res = response.body().string();
                    Log.d("GeminiRawResponse", res);

                    runOnUiThread(() -> processGeminiResponse(res));
                } else {
                    runOnUiThread(() -> addMessage("⚠️ API Error: " + response.message(), false));
                }
            }
        });
    }

    private void processGeminiResponse(String rawResponse) {
        try {
            JSONObject jsonResponse = new JSONObject(rawResponse);
            JSONArray candidates = jsonResponse.optJSONArray("candidates");

            if (candidates != null && candidates.length() > 0) {
                JSONObject first = candidates.optJSONObject(0);
                if (first != null) {
                    JSONObject content = first.optJSONObject("content");
                    if (content != null) {
                        JSONArray parts = content.optJSONArray("parts");
                        if (parts != null && parts.length() > 0) {
                            String reply = parts.getJSONObject(0).optString("text", "").trim();
                            String cleaned = cleanResponse(reply);

                            handleParsedData(cleaned);
                        }
                    }
                }
            } else {
                addMessage("⚠️ No valid response from Gemini.", false);
            }
        } catch (JSONException e) {
            Log.e("JSON_PARSE_ERROR", "Parsing error", e);
            addMessage("⚠️ Parsing error: " + e.getMessage(), false);
        }
    }

    private String cleanResponse(String response) {
        return response.replaceAll("(?s)```json", "")
                .replaceAll("(?s)```", "")
                .replaceAll("(?i)note:.*", "")
                .trim();
    }

    private void handleParsedData(String data) {
        if (data.startsWith("[") && data.endsWith("]")) {
            try {
                JSONArray jsonArray = new JSONArray(data);
                parseAndDisplayGraph(jsonArray);
            } catch (JSONException e) {
                Log.e("JSON_PARSE_ERROR", "Invalid JSON array", e);
                addMessage("⚠️ Response was not a valid JSON array.", false);
            }
        } else {
            addMessage(data, false);
        }
    }

    private void parseAndDisplayGraph(JSONArray dataArray) {
        try {
            ArrayList<Entry> entries = new ArrayList<>();

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                int year = item.getInt("year");
                float value = (float) item.getDouble("value");
                entries.add(new Entry(year, value));
            }

            lineChart.clear();

            LineDataSet dataSet = new LineDataSet(entries, "Gemini Data");
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(4f);
            dataSet.setValueTextSize(10f);

            LineData lineData = new LineData(dataSet);
            lineChart.setData(lineData);
            lineChart.getDescription().setText("Year vs Value");

            findViewById(R.id.chart_container).setVisibility(View.VISIBLE);
            lineChart.invalidate();

        } catch (JSONException e) {
            Log.e("GRAPH_ERROR", "Graph parsing error", e);
            addMessage("📉 Graph parsing error: " + e.getMessage(), false);
        }
    }
}
