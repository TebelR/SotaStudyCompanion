package a4;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

public class SotaAudioPlayer {

        private final String TARGET_MIXER = "CODEC [plughw:2,0]";

        private Mixer.Info mixer = null;

        public SotaAudioPlayer() {
            mixer = getMixerByName(TARGET_MIXER);
        }

        public void playAudio(ByteArrayInputStream wavbuffer) {  // file as .wav
            try(AudioInputStream audioStream = AudioSystem.getAudioInputStream(wavbuffer)) {
                playAudio(audioStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void playAudio(AudioInputStream audioStream) {
            try {
                AudioFormat format = audioStream.getFormat();
                SourceDataLine line = AudioSystem.getSourceDataLine(format, mixer);

                line.open(format);
                line.start();
                byte[] buffer = new byte[4096];
                int bytesRead;
                int bytesWritten = 0;
                while ((bytesRead = audioStream.read(buffer)) != -1) {
                    line.write(buffer, 0, bytesRead);
                    bytesWritten+=bytesRead;
                }

                int extraNeeded = line.getBufferSize() - bytesWritten%line.getBufferSize();
                byte[] extraZeros = new byte[extraNeeded];
                Arrays.fill(extraZeros, (byte)0);
                line.write(extraZeros, 0, extraZeros.length);

                line.drain();
                line.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void playAudio(String filename) {  // file as .wav
            if(mixer == null) {
                System.out.println("Audio mixer not initialized");
                return;
            }

            File audioFile = new File(filename);
            try {
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
                playAudio(audioStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Mixer.Info getMixerByName(String mixerName) {
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            for(Mixer.Info mixerInfo : mixerInfos)
                if(mixerInfo.getName().equals(mixerName))
                    return mixerInfo;
            return null;
        }
    }