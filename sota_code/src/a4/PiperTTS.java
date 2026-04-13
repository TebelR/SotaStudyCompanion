package a4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

class PiperTTS {
    private String serverUrl;
    private String currentTone = "normal";
    
    public PiperTTS(String serverUrl) {
        this.serverUrl = serverUrl;
    }
    
    public void setTone(String tone) {
        this.currentTone = tone;
    }
    
    public void speak(String text) {
        try {
 
            ByteArrayOutputStream wavData = getPiperWavData(
                new PiperRequest(serverUrl, text, null, 
                    getLengthScale(), getNoiseScale(), getNoiseWScale())
            );
            SotaAudioPlayer player = new SotaAudioPlayer();
            player.playAudio(new ByteArrayInputStream(wavData.toByteArray()));
        } catch (Exception e) {
            System.err.println("TTS error: " + e.getMessage());
        }
    }
    
    private double getLengthScale() {
        switch(currentTone) {
            case "angry": return 0.85;
            case "moderate": return 1;
            default: return 1.15;
        }
    }
    


    private double getNoiseScale() {
        switch(currentTone) {
            case "angry": return 0.5;
            case "moderate": return 0.7;
            default: return 0.9;
        }
    }
    


    private double getNoiseWScale() {
        switch(currentTone) {
            case "angry": return 0.5;
            case "moderate": return 0.7;
            default: return 0.9;
        }
    }    



    public static ByteArrayOutputStream getPiperWavData(PiperRequest piperRequest) { return getPiperWavData(piperRequest, false);}
    public static ByteArrayOutputStream getPiperWavData(PiperRequest piperRequest, boolean debug) {
        ByteArrayOutputStream result = null;
        try {
            URL url = new URL(piperRequest.urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // JSON
            String voiceLine = piperRequest.voice == null ?
                    "" :
                    "\"voice\": \""+piperRequest.voice+"\", ";
            String jsonInputString = "{\"text\": \"" + piperRequest.textToGen + "\"," +   // see https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/API_HTTP.md
                    voiceLine+
                    "\"sample_rate\": 22050," +  // ensure match to Sota's working rate
                    "\"length_scale\": "+ piperRequest.lengthScale +"," +
                    "\"noise_scale\": "+ piperRequest.noiseScale +"," +
                    "\"noise_w_scale\": "+piperRequest.noiseWScale+"}";
            if (debug) System.out.println("Connecting...");
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                if (debug) System.out.println("Sending Request");
                os.write(input, 0, input.length);
            }

            if (debug) System.out.println("Reading response");
            ByteArrayOutputStream wavBuffer = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    wavBuffer.write(buffer, 0, bytesRead);
                }
            }
            if (debug) System.out.println("Response received successfully");
            result = wavBuffer;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}



class PiperRequest {
    public final String urlString;
    public String textToGen;
    public double lengthScale;
    public double noiseScale; 
    public double noiseWScale; 
    public String voice;

    public PiperRequest (String urlString, String textToGen, String voice, double lengthScale, double noiseScale, double noiseWScale) {
        this.urlString = urlString;
        this.textToGen = textToGen;
        this.lengthScale = lengthScale;
        this.noiseScale = noiseScale;
        this.noiseWScale = noiseWScale;
        this.voice = voice;
    }
}
