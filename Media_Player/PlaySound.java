
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.DataLine.Info;


public class PlaySound {

    private InputStream waveStream;

    private final int EXTERNAL_BUFFER_SIZE = 4000; // this is how many bytes we need for each video frame
	private AudioInputStream audioInputStream;
	private AudioFormat audioFormat;
	private Info info;
	private SourceDataLine dataLine;
	private int readBytes = 0;
	private byte[] audioBuffer;

    /**
     * CONSTRUCTOR
     */
    public PlaySound(InputStream waveStream) {
    	this.waveStream = waveStream;
	    audioBuffer = new byte[this.EXTERNAL_BUFFER_SIZE];
    }

    public synchronized int getReadBytes() {
        return readBytes;
    }

    public void initSound() throws PlayWaveException {
        audioInputStream = null;
        try {
            //add buffer for mark/reset support
            InputStream bufferedIn = new BufferedInputStream(this.waveStream);
            audioInputStream = AudioSystem.getAudioInputStream(bufferedIn);
        } catch (UnsupportedAudioFileException e1) {
            throw new PlayWaveException(e1);
        } catch (IOException e1) {
            throw new PlayWaveException(e1);
        }

        // Obtain the information about the AudioInputStream
        audioFormat = audioInputStream.getFormat();
        info = new Info(SourceDataLine.class, audioFormat);

        // opens the audio channel
        dataLine = null;
        try {
            dataLine = (SourceDataLine) AudioSystem.getLine(info);
            dataLine.open(audioFormat, this.EXTERNAL_BUFFER_SIZE);
        } catch (LineUnavailableException e1) {
            throw new PlayWaveException(e1);
        }

        // Starts the music :P
        dataLine.start();
    }

    public void playAll() throws PlayWaveException {
        try {
            while (readBytes != -1) {
                readBytes = audioInputStream.read(audioBuffer, 0,
                        audioBuffer.length);
                if (readBytes >= 0){
                    dataLine.write(audioBuffer, 0, readBytes);
                }
            }
        } catch (IOException e1) {
            throw new PlayWaveException(e1);
        } finally {
            // plays what's left and and closes the audioChannel
            dataLine.drain();
            dataLine.close();
        }
    }

    public void playNextChunk() throws PlayWaveException {
        try {
            readBytes = audioInputStream.read(audioBuffer, 0,
                    audioBuffer.length);
            if (readBytes >= 0){
                dataLine.write(audioBuffer, 0, readBytes);
            } else {
                // plays what's left and and closes the audioChannel
                dataLine.drain();
                dataLine.close();
            }
        } catch (IOException e1) {
            throw new PlayWaveException(e1);
        }
    }

    public AudioInputStream getAudioInputStream() {
        return audioInputStream;
    }

    public void play() throws PlayWaveException {

	AudioInputStream audioInputStream = null;
	try {
	    audioInputStream = AudioSystem.getAudioInputStream(this.waveStream);
	} catch (UnsupportedAudioFileException e1) {
	    throw new PlayWaveException(e1);
	} catch (IOException e1) {
	    throw new PlayWaveException(e1);
	}

	// Obtain the information about the AudioInputStream
	AudioFormat audioFormat = audioInputStream.getFormat();
	Info info = new Info(SourceDataLine.class, audioFormat);

	// opens the audio channel
	SourceDataLine dataLine = null;
	try {
	    dataLine = (SourceDataLine) AudioSystem.getLine(info);
	    dataLine.open(audioFormat, this.EXTERNAL_BUFFER_SIZE);
	} catch (LineUnavailableException e1) {
	    throw new PlayWaveException(e1);
	}

	// Starts the music :P
	dataLine.start();

	int readBytes = 0;
	byte[] audioBuffer = new byte[this.EXTERNAL_BUFFER_SIZE];

	try {
	    while (readBytes != -1) {
		readBytes = audioInputStream.read(audioBuffer, 0,
			audioBuffer.length);
		if (readBytes >= 0){
		    dataLine.write(audioBuffer, 0, readBytes);
		}
	    }
	} catch (IOException e1) {
	    throw new PlayWaveException(e1);
	} finally {
	    // plays what's left and and closes the audioChannel
	    dataLine.drain();
	    dataLine.close();
	}

    }

    public void skipNBytes(long n) throws PlayWaveException {
        try {
            if (n > 0) {
                //audioInputStream.skip(n);
                MyPlayer.mySkip(audioInputStream, n);
            }
        } catch (IOException e1) {
            throw new PlayWaveException(e1);
        }
    }

    public void closeLine() {
	    dataLine.drain();
	    dataLine.close();
    }

    public AudioFormat getAudioFormat() {
        return audioFormat;
    }
}
