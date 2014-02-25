
import java.util.*;

/**
 * This class contains information (and methods for 
 * their derivation) about the scene including
 * statrt/stop positions, combined frame errors, 
 * and other metrics.
 *
 * Scene is a group of consecutive shots that are similar
 * (conceptually may be shot at a single time and place)
 */
public class Scene {

    private long sceneVideoStartPositionBytes;
    private long sceneVideoEndPositionBytes;
    private long sceneAudioStartPositionBytes;
    private long sceneAudioEndPositionBytes;
    private long sceneStartPositionMillis;
    private long sceneEndPositionMillis;
    private List<Shot> sceneShots;
    private double[] aveRGB;
    private double aveY;
    private double aveAudioIntensity;
    private HashMap<Integer, Integer> DCTmap;
    private boolean ad;
    private int aveMotionPerSecond;
    private boolean hasSilence;
    private int scorePoints = 0;

    public Scene(long startVBytes, long endVBytes, long startABytes, long endABytes, long startMillis, long endMillis, List<Shot> shots) {
        this.sceneVideoStartPositionBytes = startVBytes;
        this.sceneVideoEndPositionBytes = endVBytes;
        this.sceneAudioStartPositionBytes = startABytes;
        this.sceneAudioEndPositionBytes = endABytes;
        this.sceneStartPositionMillis = startMillis;
        this.sceneEndPositionMillis = endMillis;
        if (shots != null) {
            this.sceneShots = shots;
        } else {
            this.sceneShots = new ArrayList<Shot>();
        }

        DCTmap = new HashMap<Integer,Integer>();
    }

    public void addShot(Shot s) {
        if (s != null) {
            sceneShots.add(s);
        }
    }

    public long getSceneVideoEndPositionBytes() {
        return sceneVideoEndPositionBytes;
    }

    public long getSceneVideoStartPositionBytes() {
        return sceneVideoStartPositionBytes;
    }

    public long getSceneAudioEndPositionBytes() {
        return sceneAudioEndPositionBytes;
    }

    public long getSceneAudioStartPositionBytes() {
        return sceneAudioStartPositionBytes;
    }

	public double[] getAveRGB() {
		return aveRGB;
	}

	public void setAveRGB(double RAve, double GAve, double BAve ) {
		
		this.aveRGB = new double[] {RAve, GAve, BAve};
	}

	public boolean isAd() {
		return ad;
	}

	public void setAd(boolean ad) {
		this.ad = ad;
	}
	
	public double getShotPerSec(){
		return (double)sceneShots.size()/((double) (sceneEndPositionMillis-sceneStartPositionMillis)/1000.0);
	}

	public int shotNum(){
		return sceneShots.size();
	}
	
	public long getByteLength(){
		return ((sceneVideoEndPositionBytes-sceneVideoStartPositionBytes)/3);
	}

	public double getAveAudioIntensity() {
		return aveAudioIntensity;
	}

	public void setAveAudioIntensity(double aveAudioIntensity) {
		this.aveAudioIntensity = aveAudioIntensity;
	}

	public int getDCTmap(int key) {
		return DCTmap.get(key);
	}

	public void putDCTmap(int key, int value) {
		DCTmap.put(key, value);
	}

    public int getNumOfFrames() {
        return (int) Math.round( (sceneEndPositionMillis - sceneStartPositionMillis) / (1000/24.0) );
    }

    public int getNumOfBlackFrames() {
        int numBlack = 0;
        for (Shot s : sceneShots) {
            numBlack += s.getNumOfBlackFrames();
        }
        return numBlack;
    }

    /**
     * Returns boolean if scene is composed of mostly black 
     * frames (> 90% of frames are black)
     */
    public boolean isBlackScene() {
//if(getNumOfBlackFrames() > 0) { System.err.println("Requested isBlack: " + getNumOfBlackFrames()); 
//System.err.println("Number: " + getNumOfBlackFrames()/getNumOfFrames() + "\n\n\n"); }
        return (((double)getNumOfBlackFrames()/(double)getNumOfFrames()) >= 0.8);
    }

    public void setAveMotionPerSec(int m) {
        if (m >= 0) {
            this.aveMotionPerSecond = m;
        }
    }

    public int getAveMotionPerSecond() {
        return aveMotionPerSecond;
    }
    
    public long getSceneStartPositionMillis(){
    	return sceneStartPositionMillis;
    }
    
    public long getSceneEndPositionMillis(){
    	return sceneEndPositionMillis;
    }

	public double getAveY() {
		return aveY;
	}

	public void setAveY(double aveY) {
		this.aveY = aveY;
	}

	public double getSceneLengthMillis(){
		return (getSceneEndPositionMillis()-getSceneStartPositionMillis());
	}

    public void setHasSilence(boolean s) {
        hasSilence = s;
    }

    public boolean hasSilence() {
        return hasSilence;
    }

    public double getLengthInSeconds() {
        return ((double) (sceneEndPositionMillis - sceneStartPositionMillis) / 1000.0);
    }

    public double getLengthInMillis() {
        return (double) (sceneEndPositionMillis - sceneStartPositionMillis);
    }

    public long getSceneStartPositionInMillis() {
        return sceneStartPositionMillis;
    } 

    public long getSceneEndPositionInMillis() {
        return sceneEndPositionMillis;
    } 

    public void addScorePoints(int p) {
        if (p > 0) {
            scorePoints += p;
        }
    }

    public int getScorePoints() {
        return scorePoints;
    }

}

