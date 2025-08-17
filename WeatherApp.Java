package weather;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// JavaFX in Swing
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.layout.StackPane;

/**
 * Complex Weather App - Swing + JavaFX (LineChart)
 * Features:
 *  - Search city, choose units (C/F)
 *  - Current weather view
 *  - 5-day forecast chart
 *  - In-memory caching with TTL
 *  - Mock/offline mode if API key is empty
 */
public class WeatherApp extends JFrame {

    // ====== CONFIG ======
    private static final String API_KEY = ""; // <-- put your OpenWeatherMap key here
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // ====== UI FIELDS ======
    private JTextField cityField;
    private JComboBox<String> unitBox;
    private JButton searchBtn;
    private JLabel currentTempLbl, feelsLikeLbl, humidityLbl, windLbl, descLbl, cityLbl, updatedLbl;
    private JPanel fxChartHolder;
    private JFXPanel fxPanel;

    // ====== SERVICES ======
    private final WeatherService service = new WeatherService(API_KEY);
    private final ForecastChart chart = new ForecastChart();

    public WeatherApp() {
        setTitle("Complex Weather - Swing + JavaFX");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        initUI();
    }

    private void initUI() {
        var root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        // Top search bar
        var top = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6,6,6,6);
        gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;

        cityField = new JTextField("Dhaka");
        unitBox = new JComboBox<>(new String[]{"Metric (°C)","Imperial (°F)"});
        searchBtn = new JButton("Search");

        gc.gridx=0; gc.gridy=0; top.add(new JLabel("City:"), gc);
        gc.gridx=1; top.add(cityField, gc);
        gc.gridx=2; gc.weightx=0; top.add(new JLabel("Units:"), gc);
        gc.gridx=3; top.add(unitBox, gc);
        gc.gridx=4; top.add(searchBtn, gc);

        root.add(top, BorderLayout.NORTH);

        // Center - split: left details, right chart
        var center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        center.setResizeWeight(0.35);
        root.add(center, BorderLayout.CENTER);

        // Left panel - current weather cards
        var left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(new EmptyBorder(8, 8, 8, 8));

        cityLbl = labelBig("—");
        descLbl = labelMid("—");
        currentTempLbl = labelHuge("—");
        feelsLikeLbl = labelMid("Feels like: —");
        humidityLbl = labelMid("Humidity: —");
        windLbl = labelMid("Wind: —");
        updatedLbl = labelSmall("Updated: —");

        left.add(card(cityLbl));
        left.add(card(descLbl));
        left.add(card(currentTempLbl));
        left.add(card(feelsLikeLbl));
        left.add(card(humidityLbl));
        left.add(card(windLbl));
        left.add(Box.createVerticalStrut(6));
        left.add(updatedLbl);

        center.setLeftComponent(new JScrollPane(left));

        // Right panel - JavaFX chart inside Swing
        fxChartHolder = new JPanel(new BorderLayout());
        fxPanel = new JFXPanel(); // initializes JavaFX runtime on first creation
        fxChartHolder.add(fxPanel, BorderLayout.CENTER);
        center.setRightComponent(fxChartHolder);

        // Action
        searchBtn.addActionListener(this::onSearch);
        // Trigger initial load
        SwingUtilities.invokeLater(() -> searchBtn.doClick());
    }

    private JPanel card(JComponent inner) {
        var p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230,230,230)),
                new EmptyBorder(10,12,10,12)
        ));
        p.add(inner, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, inner.getPreferredSize().height + 24));
        return p;
    }

    private JLabel labelHuge(String t){ var l = new JLabel(t); l.setFont(l.getFont().deriveFont(Font.BOLD, 32f)); return l; }
    private JLabel labelBig(String t){ var l = new JLabel(t); l.setFont(l.getFont().deriveFont(Font.BOLD, 22f)); return l; }
    private JLabel labelMid(String t){ var l = new JLabel(t); l.setFont(l.getFont().deriveFont(Font.PLAIN, 16f)); return l; }
    private JLabel labelSmall(String t){ var l = new JLabel(t); l.setFont(l.getFont().deriveFont(Font.ITALIC, 12f)); l.setForeground(Color.GRAY); return l; }

    private void onSearch(ActionEvent e) {
        String city = cityField.getText().trim();
        boolean metric = unitBox.getSelectedIndex() == 0;

        if (city.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a city.");
            return;
        }

        searchBtn.setEnabled(false);
        searchBtn.setText("Loading...");
        new Thread(() -> {
            try {
                var current = service.getCurrentWeather(city, metric);
                var forecast = service.getFiveDayForecast(city, metric);

                SwingUtilities.invokeLater(() -> {
                    updateCurrentUI(current, metric);
                    updateChart(forecast, metric);
                    updatedLbl.setText("Updated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "Failed to fetch weather. " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
                );
            } finally {
                SwingUtilities.invokeLater(() -> {
                    searchBtn.setEnabled(true);
                    searchBtn.setText("Search");
                });
            }
        }).start();
    }

    private void updateCurrentUI(CurrentWeather cw, boolean metric) {
        String unit = metric ? "°C" : "°F";
        cityLbl.setText(cw.city + ", " + cw.country);
        descLbl.setText(capitalize(cw.description));
        currentTempLbl.setText(String.format(Locale.US, "%.1f%s", cw.temp, unit));
        feelsLikeLbl.setText(String.format(Locale.US, "Feels like: %.1f%s", cw.feelsLike, unit));
        humidityLbl.setText("Humidity: " + cw.humidity + "%");
        windLbl.setText(String.format(Locale.US, "Wind: %.1f %s", cw.windSpeed, metric ? "m/s" : "mph"));
    }

    private void updateChart(List<ForecastPoint> series, boolean metric) {
        Platform.runLater(() -> {
            Scene scene = chart.buildLineChart(series, metric);
            fxPanel.setScene(scene);
        });
    }

    private static String capitalize(String s) {
        return (s==null||s.isEmpty())? "—" : s.substring(0,1).toUpperCase() + s.substring(1);
    }

    // ====== Models ======
    static class CurrentWeather {
        String city, country, description;
        double temp, feelsLike, windSpeed;
        int humidity;
    }
    static class ForecastPoint {
        LocalDate date;
        double temp;
    }

    // ====== Weather Service with cache & mock fallback ======
    static class WeatherService {
        private final String apiKey;
        private final HttpClient http = HttpClient.newHttpClient();
        private final Gson gson = new Gson();
        private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();

        WeatherService(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey.trim();
            // Initialize JavaFX platform early for smoother first render
            try { Platform.startup(() -> {}); } catch (IllegalStateException ignore) {}
        }

        CurrentWeather getCurrentWeather(String city, boolean metric) throws IOException, InterruptedException {
            String key = "current:" + city.toLowerCase() + ":" + (metric?"C":"F");
            var cached = getCached(key, CurrentWeather.class);
            if (cached != null) return cached;

            if (apiKey.isEmpty()) {
                CurrentWeather mock = mockCurrent(city, metric);
                putCached(key, mock);
                return mock;
            }

            String units = metric ? "metric" : "imperial";
            String url = String.format("%s/weather?q=%s&appid=%s&units=%s", BASE_URL, encode(city), apiKey, units);
            var req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IOException("API error (current): " + res.statusCode());

            var obj = JsonParser.parseString(res.body()).getAsJsonObject();
            CurrentWeather cw = new CurrentWeather();
            cw.city = obj.get("name").getAsString();
            cw.country = obj.getAsJsonObject("sys").get("country").getAsString();
            var weatherArr = obj.getAsJsonArray("weather");
            cw.description = weatherArr.size()>0 ? weatherArr.get(0).getAsJsonObject().get("description").getAsString() : "—";
            var main = obj.getAsJsonObject("main");
            cw.temp = main.get("temp").getAsDouble();
            cw.feelsLike = main.get("feels_like").getAsDouble();
            cw.humidity = main.get("humidity").getAsInt();
            cw.windSpeed = obj.getAsJsonObject("wind").get("speed").getAsDouble();

            putCached(key, cw);
            return cw;
        }

        List<ForecastPoint> getFiveDayForecast(String city, boolean metric) throws IOException, InterruptedException {
            String key = "forecast:" + city.toLowerCase() + ":" + (metric?"C":"F");
            var cached = getCached(key, List.class);
            if (cached != null) return (List<ForecastPoint>) cached;

            if (apiKey.isEmpty()) {
                var mock = mockForecast(metric);
                putCached(key, mock);
                return mock;
            }

            String units = metric ? "metric" : "imperial";
            String url = String.format("%s/forecast?q=%s&appid=%s&units=%s", BASE_URL, encode(city), apiKey, units);
            var req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IOException("API error (forecast): " + res.statusCode());

            var obj = JsonParser.parseString(res.body()).getAsJsonObject();
            var list = obj.getAsJsonArray("list");

            // aggregate 3-hour points to daily averages
            Map<LocalDate, List<Double>> daily = new LinkedHashMap<>();
            for (var el : list) {
                var o = el.getAsJsonObject();
                long epoch = o.get("dt").getAsLong();
                LocalDate date = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
                double t = o.getAsJsonObject("main").get("temp").getAsDouble();
                daily.computeIfAbsent(date, k -> new ArrayList<>()).add(t);
            }

            List<ForecastPoint> out = new ArrayList<>();
            for (var entry : daily.entrySet()) {
                double avg = entry.getValue().stream().mapToDouble(d->d).average().orElse(Double.NaN);
                ForecastPoint fp = new ForecastPoint();
                fp.date = entry.getKey();
                fp.temp = avg;
                out.add(fp);
            }
            // keep next 5 days
            out.sort(Comparator.comparing(fp -> fp.date));
            out = out.subList(0, Math.min(5, out.size()));

            putCached(key, out);
            return out;
        }

        // --- cache helpers ---
        private <T> void putCached(String key, T value) {
            cache.put(key, new CacheEntry<>(value, Instant.now()));
        }
        private <T> T getCached(String key, Class<T> type) {
            var entry = cache.get(key);
            if (entry == null) return null;
            if (Duration.between(entry.timestamp, Instant.now()).compareTo(CACHE_TTL) > 0) {
                cache.remove(key); return null;
            }
            return type.cast(entry.value);
        }
        record CacheEntry<T>(T value, Instant timestamp) {}

        // --- mock data for offline/dev ---
        private CurrentWeather mockCurrent(String city, boolean metric) {
            CurrentWeather cw = new CurrentWeather();
            cw.city = capitalize(city);
            cw.country = "XX";
            cw.description = "scattered clouds";
            cw.temp = metric ? 30.2 : 86.4;
            cw.feelsLike = metric ? 34.0 : 93.2;
            cw.humidity = 68;
            cw.windSpeed = metric ? 3.8 : 8.5;
            return cw;
        }
        private List<ForecastPoint> mockForecast(boolean metric) {
            List<ForecastPoint> l = new ArrayList<>();
            LocalDate today = LocalDate.now();
            double base = metric ? 30 : 86;
            for (int i=0;i<5;i++){
                ForecastPoint fp = new ForecastPoint();
                fp.date = today.plusDays(i);
                fp.temp = base + Math.sin(i)*2;
                l.add(fp);
            }
            return l;
        }

        private static String encode(String s) {
            return s.replace(" ", "%20");
        }
    }

    // ====== JavaFX chart builder ======
    static class ForecastChart {
        Scene buildLineChart(List<ForecastPoint> points, boolean metric) {
            final String unit = metric ? "°C" : "°F";
            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel("Date");
            yAxis.setLabel("Avg Temp (" + unit + ")");

            LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
            lineChart.setLegendVisible(false);
            lineChart.setAnimated(false);
            lineChart.setTitle("5-Day Forecast");

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE dd");
            for (var p : points) {
                series.getData().add(new XYChart.Data<>(p.date.format(fmt), p.temp));
            }
            lineChart.getData().add(series);

            StackPane root = new StackPane(lineChart);
            return new Scene(root, 600, 400);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new WeatherApp().setVisible(true));
    }
}
