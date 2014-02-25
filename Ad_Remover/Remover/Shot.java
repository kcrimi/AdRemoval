
import java.util.*;

/**
 * This class contains information (and methods for 
 * their derivation) about the shot including
 * shot statrt/stop positions, combined frame errors, 
 * and other metrics.
 */
public class Shot {

    private long shotVideoStartPositionBytes;
    private long shotVideoEndPositionBytes;
    private long shotAudioStartPositionBytes;
    private long shotAudioEndPositionBytes;
    private long shotStartPositionMillis;
    private long shotEndPositionMillis;
    private int numOfBlackFrames;
    private int aveMotionPerSecond;


    public Shot(long startVBytes, long endVBytes, long startABytes, long endABytes, long startMillis, long endMillis) {
        this.shotVideoStartPositionBytes = startVBytes;
        this.shotVideoEndPositionBytes = endVBytes;
        this.shotAudioStartPositionBytes = startABytes;
        this.shotAudioEndPositionBytes = endABytes;
        this.shotStartPositionMillis = startMillis;
        this.shotEndPositionMillis = endMillis;
        this.numOfBlackFrames = 0;
    }

    public int getNumOfFrames() {
        return (int) Math.round( (shotEndPositionMillis - shotStartPositionMillis) / (1000/24.0) );
    }

    public void setNumOfBlackFrames(int n) {
        if (n >= 0) {
            this.numOfBlackFrames = n;
        }
    }

    public int getNumOfBlackFrames() {
        return numOfBlackFrames;
    }

    public boolean isBlackShot() {
        //return (getNumOfFrames() == getNumOfBlackFrames());
        return ((double) getNumOfBlackFrames()/(double) getNumOfFrames() > 0.8);
    }

    public void setAveMotionPerSec(int m) {
        if (m >= 0) {
            this.aveMotionPerSecond = m;
        }
    }

    public int getAveMotionPerSecond() {
        return aveMotionPerSecond;
    }

}

