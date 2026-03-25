package com.openpositioning.PositionMe.sensors;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages WiFi scan result processing and WiFi-based positioning requests.
 *
 * <p>Implements {@link Observer} to receive updates from {@link WifiDataProcessor},
 * replacing the role previously held by {@link SensorFusion}.</p>
 *
 * @see WifiDataProcessor the observable that triggers WiFi scan updates
 * @see WiFiPositioning   the API client for WiFi-based positioning
 */
public class WifiPositionManager implements Observer {

    private static final String WIFI_FINGERPRINT = "wf";

    private final WiFiPositioning wiFiPositioning;
    private final TrajectoryRecorder recorder;
    private List<Wifi> wifiList;

    /**
     * Creates a new WifiPositionManager.
     *
     * @param wiFiPositioning WiFi positioning API client
     * @param recorder        trajectory recorder for writing WiFi fingerprints
     */
    public WifiPositionManager(WiFiPositioning wiFiPositioning,
                               TrajectoryRecorder recorder) {
        this.wiFiPositioning = wiFiPositioning;
        this.recorder = recorder;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Receives updates from {@link WifiDataProcessor}. Converts the raw object array
     * to a typed list, delegates fingerprint recording to {@link TrajectoryRecorder},
     * and triggers a WiFi positioning request.</p>
     */
    @Override
    public void update(Object[] wifiList) {
        android.util.Log.d("WifiProbe", "太好了！接收到了WiFi扫描数据！");
        this.wifiList = Stream.of(wifiList).map(o -> (Wifi) o).filter(w -> w.getLevel() < -40).collect(Collectors.toList());
        recorder.addWifiFingerprint(this.wifiList);
        // 改为调用带回调的方法：
        createWifiPositionRequestCallback();
    }

    /**
     * Creates a request to obtain a WiFi location for the obtained WiFi fingerprint.
     */
    private void createWifiPositioningRequest() {
        try {
            JSONObject wifiAccessPoints = new JSONObject();
            for (Wifi data : this.wifiList) {
                wifiAccessPoints.put(String.valueOf(data.getBssid()), data.getLevel());
            }
            JSONObject wifiFingerPrint = new JSONObject();
            wifiFingerPrint.put(WIFI_FINGERPRINT, wifiAccessPoints);
            this.wiFiPositioning.request(wifiFingerPrint);
        } catch (JSONException e) {
            android.util.Log.e("WifiProbe", "JSON打包失败: " + e.toString());
            Log.e("jsonErrors", "Error creating json object" + e.toString());
        }
    }

    /**
     * Creates a WiFi positioning request using the Volley callback pattern.
     */
    private void createWifiPositionRequestCallback() {
        try {
            JSONObject wifiAccessPoints = new JSONObject();
            for (Wifi data : this.wifiList) {
                wifiAccessPoints.put(String.valueOf(data.getBssid()), data.getLevel());
            }
            JSONObject wifiFingerPrint = new JSONObject();
            wifiFingerPrint.put(WIFI_FINGERPRINT, wifiAccessPoints);
            android.util.Log.d("WifiProbe", "准备发送网络请求到服务器...");
            this.wiFiPositioning.request(wifiFingerPrint, new WiFiPositioning.VolleyCallback() {
                @Override
                public void onSuccess(LatLng wifiLocation, int floor) {
                    Log.d("WifiSuccess", "✅✅✅ 融合系统已接收WiFi坐标，准备更新粒子！✅✅✅");
                    // --- 开始接入我们的粒子滤波器 ---
                    com.openpositioning.PositionMe.fusion.ParticleFilter pf = SensorFusion.getInstance().getParticleFilter();

                    if (pf != null && wifiLocation != null) {

                        // 1. 获取起始点 GPS 坐标作为本地坐标系的原点 (0,0)
                        float[] startLoc = SensorFusion.getInstance().getGNSSLatitude(true);
                        double lat0 = startLoc[0]; // 起点纬度
                        double lng0 = startLoc[1]; // 起点经度

                        // 2. 将 WiFi 返回的 WGS84 经纬度转换为以米为单位的局部坐标 (X, Y)
                        // 使用简单的平地近似公式 (Flat Earth Approximation) / 局部切面投影
                        double R = 6378137.0; // 地球赤道半径（米）
                        double lat = wifiLocation.latitude;
                        double lng = wifiLocation.longitude;

                        // 计算经纬度差值并转为弧度
                        double dLat = Math.toRadians(lat - lat0);
                        double dLng = Math.toRadians(lng - lng0);

                        // 计算 X 和 Y (米)
                        // X = 经度差 * 赤道半径 * cos(起点纬度)
                        // Y = 纬度差 * 赤道半径
                        float x = (float) (R * dLng * Math.cos(Math.toRadians(lat0)));
                        float y = (float) (R * dLat);

                        // 3. 创建 Measurement 对象
                        // 假设 WiFi 定位误差约为 5.0 米 (如果没有具体数值，这里预设一个合理的 sigma)
                        com.openpositioning.PositionMe.fusion.Measurement m =
                                new com.openpositioning.PositionMe.fusion.Measurement(x, y, 10.0);

                        // 4. 万事俱备！更新权重并重采样！
                        pf.updateWeights(m);
                        pf.resample();

                        // 打印出融合后的最新位置，用于我们在控制台观察它是否生效
                        com.openpositioning.PositionMe.fusion.Position pos = pf.getEstimatedPosition();
                        Log.d("ParticleFilter", "融合后的平滑坐标 X: " + pos.x + " Y: " + pos.y);
                    }
                    // --- 粒子滤波器接入结束 ---
                }

                @Override
                public void onError(String message) {
                    // Handle the error response
                    android.util.Log.e("WifiProbe", "服务器报错了: " + message);
                }
            });
        } catch (JSONException e) {
            Log.e("jsonErrors", "Error creating json object" + e.toString());
        }
    }

    /**
     * Returns the user position obtained using WiFi positioning.
     *
     * @return {@link LatLng} corresponding to the user's position
     */
    public LatLng getLatLngWifiPositioning() {
        return this.wiFiPositioning.getWifiLocation();
    }

    /**
     * Returns the current floor the user is on, obtained using WiFi positioning.
     *
     * @return current floor number
     */
    public int getWifiFloor() {
        return this.wiFiPositioning.getFloor();
    }

    /**
     * Returns the most recent list of WiFi scan results.
     *
     * @return list of {@link Wifi} objects
     */
    public List<Wifi> getWifiList() {
        return this.wifiList;
    }
}
