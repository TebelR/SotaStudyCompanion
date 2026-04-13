package a4;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;


public class GazeTrackerClient {
    private String url;
    private boolean lastDistracted = false;
    
    public GazeTrackerClient(String laptopIp, int port) {
        this.url = "http://" + laptopIp + ":" + port + "/detect";
    }
    
    public int updateAndGetDistracted(byte[] bgrFrame) {
        HttpURLConnection conn = null;
        try {
            URL urlObj = new URL(url);
            conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            
            // Send the frame
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bgrFrame);
                os.flush();
            }
            
            // Check response
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                // Read response body
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                String json = response.toString();
                // Parse just the distracted boolean
                lastDistracted = json.contains("\"distracted\": true") || 
                                 json.contains("\"distracted\":true");
                return lastDistracted ? 1 : 0;
            } else {
                System.err.println("Gaze update HTTP error: " + responseCode);
                return -1;
            }
            
        } catch (Exception e) {
            System.err.println("Gaze update failed: " + e.getMessage());
            return -1;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    public boolean isDistracted() {
        return lastDistracted;
    }
}
    