package a4;

import javax.sound.sampled.*;

import pocketsphinx.PocketSphinx;
import pocketsphinx.RecognitionResult;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceKeywordDetector {
    private PocketSphinx sphinx;
    private long decoderPtr;
    private TargetDataLine microphone;
    private Thread listeningThread;
    private AtomicBoolean isListening = new AtomicBoolean(false);
    private ConcurrentLinkedQueue<String> detectedKeywords = new ConcurrentLinkedQueue<>();

    Blackboard blackboard;

    // Paths to PocketSphinx models
    private static final String MODEL_PATH = "/home/root/sotaprograms/resources/sphinxmodel/en-us/en-us";
    private static final String KEYPHRASES_PATH = "/home/root/sotaprograms/jars/keyphrases.txt";
    private static final String DICT_PATH = "/home/root/sotaprograms/resources/sphinxmodel/en-us/cmudict-en-us.dict";
    
    public VoiceKeywordDetector( Blackboard blackboard) {
        try {
            sphinx = new PocketSphinx();
            
            File keyphraseFile = new File(KEYPHRASES_PATH);
            if (!keyphraseFile.exists()) {
                createDefaultKeyphraseFile();
            }

            decoderPtr = sphinx.initialize_kws(
                MODEL_PATH, 
                KEYPHRASES_PATH, 
                DICT_PATH
            );

            this.blackboard = blackboard;
            
            if (decoderPtr == 0) {
                System.err.println("[VOICE] Failed to initialize PocketSphinx");
            } else {
                System.out.println("[VOICE] PocketSphinx initialized successfully");
            }
            
        } catch (Exception e) {
            System.err.println("[VOICE] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }
    

    // For ease of use - just creates the keyphrases file
    private void createDefaultKeyphraseFile() {
        try {
            File file = new File(KEYPHRASES_PATH);
            file.getParentFile().mkdirs();
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("end /1e-15/");
                writer.println("break /1e-10/");
                writer.println("work /1e-15/");
                writer.println("yes /1e-10/");
            }
            System.out.println("[VOICE] Created default keyphrase file at: " + KEYPHRASES_PATH);
        } catch (IOException e) {
            System.err.println("[VOICE] Failed to create keyphrase file: " + e.getMessage());
        }
    }
    


    public void startListening() {
        if (decoderPtr == 0) {
            System.err.println("[VOICE] Cannot start - decoder not initialized");
            return;
        }
        
        if (isListening.get()) {
            System.out.println("[VOICE] Already listening");
            return;
        }
        


        try {
            // Configure microphone
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();
            System.out.println("[VOICE] Microphone started");

            // Start PocketSphinx decoder
            sphinx.startListening(decoderPtr);
            
            isListening.set(true);
            
            // Start listening thread
            listeningThread = new Thread(() -> listenLoop());
            listeningThread.setPriority(Thread.MIN_PRIORITY);
            listeningThread.setDaemon(true);
            listeningThread.start();
            
        } catch (Exception e) {
            System.err.println("[VOICE] Failed to start microphone: " + e.getMessage());
        }
    }

    private boolean isSpeech(byte[] audioData) {
        // Check for peaks instead amplitude theshold - less math computation
        int peakCount = 0;
        int threshold = 1500;// Calibrate this if your sota is not hearing anything, this blocked out noise pretty well for me
        
        for (int i = 0; i < audioData.length; i += 8) {
            if (i + 1 < audioData.length) {
                short sample = (short)((audioData[i+1] << 8) | (audioData[i] & 0xFF));
                if (Math.abs(sample) > threshold) {
                    peakCount++;
                    if (peakCount > 10) {
                        System.out.println("[VOICE] Speech detected");
                        return true;
                    }
                }
            }
        }
        return false;
    }
    


    private void listenLoop() {
        int ms = 512;//256;  // Process 512ms chunks 
        int SIZE = ms * 16000 * 2 / 1000;  // 16kHz, 16-bit = 2 bytes per sample
        byte[] buffer = new byte[SIZE];
        
        while (isListening.get() && decoderPtr != 0) {

            try {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0 && isSpeech(Arrays.copyOf(buffer, bytesRead))) {
                    sphinx.processAudio(decoderPtr, buffer, bytesRead);
                    
                    RecognitionResult result = sphinx.getRecognitionHypothesis(decoderPtr);
                    if (result != null && result.result != null && !result.result.isEmpty()) {
                        String rawResult = result.result.trim().toLowerCase();
                        
                        String[] words = rawResult.split("\\s+");
                        String lastKeyword = words[words.length - 1];
                        
                        System.out.println("[VOICE] Detected keyword: " + lastKeyword);
                        blackboard.setKeyword(lastKeyword);
                    }
                }
            } catch (Exception e) {
                System.err.println("[VOICE] Error in listen loop: " + e.getMessage() + " - " + e.getCause());
            }
        }
    }
    


    public String getNextKeyword() {
        return detectedKeywords.poll();
    }
  


    public boolean hasKeyword() {
        return !detectedKeywords.isEmpty();
    }
    


    public void stopListening() {
        if (!isListening.get()) return;
        
        isListening.set(false);
        
        try {
            if (sphinx != null && decoderPtr != 0) {
                sphinx.stopListening(decoderPtr);
            }
            if (microphone != null) {
                microphone.stop();
                microphone.close();
            }
            if (listeningThread != null) {
                listeningThread.interrupt();
            }
            System.out.println("[VOICE] Stopped listening");
        } catch (Exception e) {
            System.err.println("[VOICE] Error stopping: " + e.getMessage());
        }
    }
    

    
    public void cleanup() {
        stopListening();
        if (sphinx != null && decoderPtr != 0) {
            sphinx.cleanup(decoderPtr);
        }
    }
}


