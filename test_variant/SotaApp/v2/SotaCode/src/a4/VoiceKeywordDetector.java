package a4;

import javax.sound.sampled.*;

import pocketsphinx.PocketSphinx;
import pocketsphinx.RecognitionResult;

import java.io.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceKeywordDetector {
    private PocketSphinx sphinx;
    private long decoderPtr;
    private TargetDataLine microphone;
    private Thread listeningThread;
    private AtomicBoolean isListening = new AtomicBoolean(false);
    private ConcurrentLinkedQueue<String> detectedKeywords = new ConcurrentLinkedQueue<>();
    
    // Paths to PocketSphinx models (adjust based on your Sota setup)
    private static final String MODEL_PATH = "/home/root/sotaprograms/resources/sphinxmodel/en-us/en-us";
    private static final String KEYPHRASES_PATH = "/home/root/sotaprograms/jars/keyphrases.txt";
    private static final String DICT_PATH = "/home/root/sotaprograms/resources/sphinxmodel/en-us/cmudict-en-us.dict";
    
    public VoiceKeywordDetector() {
        try {
            sphinx = new PocketSphinx();
            
            // Check if keyphrases file exists
            File keyphraseFile = new File(KEYPHRASES_PATH);
            if (!keyphraseFile.exists()) {
                createDefaultKeyphraseFile();
            }
            
            // Initialize PocketSphinx for keyword spotting
            decoderPtr = sphinx.initialize_kws(
                MODEL_PATH, 
                KEYPHRASES_PATH, 
                DICT_PATH
            );
            
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
                writer.println("done /1e-40/");
                writer.println("break /1e-40/");
                writer.println("work /1e-40/");
                writer.println("sure /1e-40/");
                writer.println("no /1e-40/");
                writer.println("yes /1e-40/");
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
            listeningThread.setDaemon(true);
            listeningThread.start();
            
            System.out.println("[VOICE] Listening for keywords: done, break, work, sure, no, yes");
            
        } catch (Exception e) {
            System.err.println("[VOICE] Failed to start microphone: " + e.getMessage());
        }
    }
    


    private void listenLoop() {
        int ms = 256;  // Process 256ms chunks
        int SIZE = ms * 16000 * 2 / 1000;  // 16kHz, 16-bit = 2 bytes per sample
        byte[] buffer = new byte[SIZE];
        
        while (isListening.get() && decoderPtr != 0) {
            try {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    sphinx.processAudio(decoderPtr, buffer, bytesRead);
                    
                    RecognitionResult result = sphinx.getRecognitionHypothesis(decoderPtr);
                    if (result != null && result.result != null && !result.result.isEmpty()) {
                        String keyword = result.result.trim().toLowerCase();
                        System.out.println("[VOICE] Detected: " + keyword + " (score: " + result.score + ")");
                        detectedKeywords.offer(keyword);
                    }
                }
            } catch (Exception e) {
                System.err.println("[VOICE] Error in listen loop: " + e.getMessage());
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