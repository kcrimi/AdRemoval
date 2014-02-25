/*
 * CSCI 576 - Final Project 
 * April 5, 2013
 */

import java.io.*;
import java.text.*;
import java.util.*;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioSystem;


public class AdRemover2 {
    // k-value for motion vector search
    private static final int K = 2;
    private static final int BLOCK_SIZE = 16;

    // defaults
    private String videoFileName = "";
    private String audioFileName = "";
    private String videoOutFileName = "";
    private String audioOutFileName = "";
    private int width = 352;
    private int height = 288;

    // buffers, input streams, etc.
    private byte[] bytesR;
    private byte[] bytesG;
    private byte[] bytesB;
    private byte[] bytesY;
    private byte[] bytesRPrevFrame;
    private byte[] bytesGPrevFrame;
    private byte[] bytesBPrevFrame;
    private byte[] bytesYPrevFrame;
    private int numRvalsInFrame;
    private int numGvalsInFrame;
    private int numBvalsInFrame;

    // motion vector search buffers
    byte[][] prevBytesY = new byte[width][height];
    byte[][] currBytesY = new byte[width][height];

//    private BufferedImage currFrame;
    //private BufferedInputStream inV;
    private InputStream inV;
    private InputStream inA;
    private AudioInputStream dataOnlyAudioStream;
    private static PlaySound playSound;
    private long totalNumFrames = 0;

    // error vals used to identify keyframes
    private List<Integer> rgbErrors;
    private List<Integer> yErrors;
    private List<Boolean> isBlackFrame;
    private List<Boolean> isZeroAudioIntensityFrame;
    private List<Integer> totFrameMotion;
    
    // average intensity values for audio frames
    private ArrayList<Integer> aveIntensities;

    // average vals used in metrics
    private ArrayList<Double> aveRVals;
    private ArrayList<Double> aveGVals;
    private ArrayList<Double> aveBVals;
    private List<List<Double>> possibleAdTimeIntervals;
    private double vidAveRVal = 0;
    private double vidAveGVal = 0;
    private double vidAveBVal = 0;
    private int shotNum = 0;
    private double vidAveAudioIntensity = 0;
    private int aveSceneLength;
    private int aveMotion;
    private int numOfLowIntensityFrames = 0;
    
    private List<Scene> scenes;
    private Integer threadLock = new Integer(0);


    public static void main(String[] args) {
        String videoFileName = "";
        String audioFileName = "";
        String videoOutFileName = "";
        String audioOutFileName = "";
        int width = 0;
        int height = 0;
        final AdRemover2 mAdRemover;

        // check all input parameters...
        if (args.length != 4 && args.length != 6) {
            throw new IllegalArgumentException("Invalid Number of arguments. Usage: java" 
                    + " AdRemover InputVideo InputAudio OutputVideo OutputAudio [Width Height]\n");
        }

        videoFileName = args[0];
        audioFileName = args[1];
        videoOutFileName = args[2];
        audioOutFileName = args[3];

        if(args.length == 6) {
            try {
                width = Integer.parseInt(args[4]);
                height = Integer.parseInt(args[5]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unable to parse one of the command line arguments.\n" + e);
            }
        }

        if(width < 0 || height < 0) {
            throw new IllegalArgumentException("Ivalid output video dimensions.\n");
        } 

        if (width > 0 && height > 0) { 
            mAdRemover = new AdRemover2(videoFileName, audioFileName, videoOutFileName, audioOutFileName, width, height);
        } else {
            mAdRemover = new AdRemover2(videoFileName, audioFileName, videoOutFileName, audioOutFileName);
        }

        System.out.println("Input Video: " + videoFileName);
        System.out.println("Input Audio: " + audioFileName);


        System.out.println("Searching for keyframes...");
        boolean frameReadSuccessful = false;
        int frame = 0;
        while(true) {
            mAdRemover.calcFrameError(frame);
            frameReadSuccessful = mAdRemover.readInNextFrame(frame);
            if(!frameReadSuccessful) { // no more valid frames
                break;
            }
            if (frame % 500 == 0) {
                System.out.println("Processed frame: " + frame);
            }
            frame++;
        }

        System.out.println("Calculating Average Audio Intensity...");
        mAdRemover.calcAbsAveAudioIntensities();
        mAdRemover.findFramesWithZeroAudioIntensities();


        // print RGB error points
        //System.out.print(mAdRemover.getRGBErrorVsTimePointsInTabSeparetedFormat());
        //System.out.print(mAdRemover.getYErrorVsTimePointsInTabSeparetedFormat());
        //System.out.print(mAdRemover.getTotFrameMotionVsTimePointsInTabSeparetedFormat());
        // print the average Audio Intensity values
        //mAdRemover.getAveIntensitiesInTabSeparetedFormat();

        // go through rgb error list and split video into shots based on it
        System.out.println("Splitting video into \"scenes of shots\"...");
        //mAdRemover.createShotsAndScenes();
        mAdRemover.createShotsAndScenesUsingYChannelErrors();

        // Metric Analysis
        System.out.println("Running scene metric analysis...");
        mAdRemover.scoreAverageRGB();
        mAdRemover.scoreAverageAudioIntensity();
        mAdRemover.runDCT();

//        mAdRemover.chooseAds();

        mAdRemover.chooseAdsUsingPointSystem();
        mAdRemover.postProcessAds();

        // resulting video to disk
        System.out.println("Writing results to disk...");
        mAdRemover.writeScenesToDisk();

        // this method will writeout only scenes in argument variable
        // used to test vector and black scenes stuff...
//        mAdRemover.writeScenesToDisk(tScenes);
//        mAdRemover.writeAdScenesToDisk();
        
        System.out.println("Complete");
    }

    public AdRemover2(String vFileName, String aFileName, String vOutFileName, String aOutFileName) {
        this.videoFileName = vFileName;
        this.audioFileName = aFileName;
        this.videoOutFileName = vOutFileName;
        this.audioOutFileName = aOutFileName;

        init();
    }

    public AdRemover2(String vFileName, String aFileName, String vOutFileName, String aOutFileName, int width, int height) {
        this.videoFileName = vFileName;
        this.audioFileName = aFileName;
        this.videoOutFileName = vOutFileName;
        this.audioOutFileName = aOutFileName;
        this.width = width;
        this.height = height;

        init();
    }

    private void init() {
        // init remaining instance vars 
        long videoFileSizeInBytes = 0;
        int totalPixelsInFrame = width * height;
        numRvalsInFrame = totalPixelsInFrame;
        numGvalsInFrame = totalPixelsInFrame;
        numBvalsInFrame = totalPixelsInFrame;
        bytesR = new byte[totalPixelsInFrame];
        bytesG = new byte[totalPixelsInFrame];
        bytesB = new byte[totalPixelsInFrame];
        bytesY = new byte[totalPixelsInFrame];
//        currFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // now open files and create input streams
        try {
            File file = new File(videoFileName);
            videoFileSizeInBytes = file.length();
            //inV = new BufferedInputStream(new FileInputStream(file));
            inV = new FileInputStream(file);
            inV.mark((int)videoFileSizeInBytes);
            file = new File(audioFileName);
            inA = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            System.err.println("Unable to open file: " + e);
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IO Error: " + e);
            e.printStackTrace();
            System.exit(1);
        }

        // initializes the playSound Object
        playSound = new PlaySound(inA);
        try {
            playSound.initSound();
        } catch (PlayWaveException e) {
            e.printStackTrace();
            return;
        }

        dataOnlyAudioStream = playSound.getAudioInputStream();
        rgbErrors = new ArrayList<Integer>();
        aveIntensities = new ArrayList<Integer>();
        yErrors = new ArrayList<Integer>();
        scenes = new ArrayList<Scene>();
        isBlackFrame = new ArrayList<Boolean>();
        isZeroAudioIntensityFrame = new ArrayList<Boolean>();
        totFrameMotion = new ArrayList<Integer>();
        possibleAdTimeIntervals = new ArrayList<List<Double>>();
        
        aveRVals = new ArrayList<Double>();
        aveGVals = new ArrayList<Double>();
        aveBVals = new ArrayList<Double>();
    }

    private boolean readInNextFrame(int frameNum) {
        synchronized(threadLock) {
        // read R,G,B bytes for next frame from video input stream
        try {
            // store prev. frame buffers (will be used to calc. frame error)
            bytesBPrevFrame = bytesB.clone(); // need deep copy
            bytesGPrevFrame = bytesG.clone();
            bytesRPrevFrame = bytesR.clone();
            bytesYPrevFrame = bytesY.clone();
            // the order is B,G,R not R,G,B as was mentioned in Assignment Readme...
            int bBytesRead = inV.read(bytesB);
            int gBytesRead = inV.read(bytesG);
            int rBytesRead = inV.read(bytesR);

            if (rBytesRead != bytesR.length || gBytesRead != bytesG.length || bBytesRead != bytesB.length) {
//                isBlackFrame.add(false);
                return false;
            }

            int ind = 0;
            int totRColor = 0;
            int totGColor = 0;
            int totBColor = 0;
            for(int y = 0; y < height; y++){
                for(int x = 0; x < width; x++){

                    byte a = 0;
                    byte r = bytesR[ind];
                    byte g = bytesG[ind];
                    byte b = bytesB[ind]; 

                    int rVal = (int) bytesR[ind] & 0xFF;
                    int gVal = (int) bytesG[ind] & 0xFF;
                    int bVal = (int) bytesB[ind] & 0xFF;
                    // store Y-value into buffer
                    bytesY[ind] = (byte) Math.round(0.299*rVal + 0.587*gVal + 0.114*bVal);

                    totRColor += rVal;
                    totGColor += gVal;
                    totBColor += bVal;

                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
//                    currFrame.setRGB(x,y,pix);
                    ind++;
                }
            }

            // calculate motion vectors using Y-channel
            int totalMotion = calcTotalMotionForTwoFrames(bytesYPrevFrame, bytesY); 
            totFrameMotion.add(totalMotion);
//            totFrameMotion.add(0);

            // check if black frame
            if (((Math.round(totRColor/(width * height))) <= 5) && 
                ((Math.round(totGColor/(width * height))) <= 5) &&
                ((Math.round(totBColor/(width * height))) <= 5)
            ) {
                isBlackFrame.add(true);
            } else {
                isBlackFrame.add(false);
            }

        } catch (IOException e) {
            System.err.println("Error reading video input file: " + e);
            e.printStackTrace();
            System.exit(1);
        }

        return true;
        }
    }

    /**
     * This method calculates RGB error between current and previous frames using
     * the following logic:
     * 1) for each channel - R, G, and B - we calculate difference between pixels in same positions in sequential frames. 
     * 2) we takes abs. values of these and sum the diffs from every channel for every pixel
     * 3) we sum diffs from all pixels from same frame. This would give us a single err. diff. value for a frame.
     * 
     * Piggybacking on frame error, stores average R, G, and B values per frame for later metric use
     */
    private void calcFrameError(int f) {
        // nothing to compare this frame with, assume error is zero.
        if (bytesRPrevFrame == null && bytesGPrevFrame == null && bytesBPrevFrame == null) {
//            rgbErrors.add(0);
//            yErrors.add(0);
            return;
        
               
        }

        int currR = 0;
        int currG = 0;
        int currB = 0;
        int currY = 0;
        int prevR = 0;
        int prevG = 0;
        int prevB = 0;
        int prevY = 0;
        int rError = 0;
        int gError = 0;
        int bError = 0;
        int yError = 0;
        double frameRAve = 0;
        double frameGAve = 0;
        double frameBAve = 0;
        
        for (int i = 0; i < bytesRPrevFrame.length; i++) {
            // since Java bytes range from -128 to +127, we need to get positive vals
            // for correct error calculation
            currR = (int) bytesR[i] & 0xFF;
            currG = (int) bytesG[i] & 0xFF;
            currB = (int) bytesB[i] & 0xFF;
            currY = (int) bytesY[i] & 0xFF;
            prevR = (int) bytesRPrevFrame[i] & 0xFF;
            prevG = (int) bytesGPrevFrame[i] & 0xFF;
            prevB = (int) bytesBPrevFrame[i] & 0xFF;
            prevY = (int) bytesYPrevFrame[i] & 0xFF;

            rError += Math.abs(currR - prevR);
            gError += Math.abs(currG - prevG);
            bError += Math.abs(currB - prevB);
            yError += Math.abs(currY - prevY);
            
            frameRAve += ((double)currR / numRvalsInFrame);
            frameGAve += ((double)currG / numGvalsInFrame);
            frameBAve += ((double)currB / numBvalsInFrame);
            
        }
        
        aveRVals.add(frameRAve);
        aveGVals.add(frameGAve);
        aveBVals.add(frameBAve);

        int finalFrameError = rError + gError + bError;
        // add frame error to the list
        rgbErrors.add(finalFrameError);
        yErrors.add(yError);
    }
 
    /**
     * This method calculates the average intensity of the audio for each frame 
     * 
     * 1) read in a frame's audio bytes
     * 2) take the average of the absolute value of the bytes
     * 3) save this to the aveIntensities variable
     * 4) also assigns the variable aveAudioIntensity to the video's average intensity
     */
    private void calcAbsAveAudioIntensities()
    {
    	resetAudioStream(0); 
        InputStream inputAudio = dataOnlyAudioStream;
        byte[] buffer = new byte[4000];
        int j = 0;
        int grandTotal = 0;
        while (j<=rgbErrors.size()) {
            try {
				inputAudio.read(buffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
            int total = 0;
            for (int byteInd = 0; byteInd < 4000; byteInd+=2) {
            	total += Math.abs((short) ((buffer[byteInd] << 8) | (buffer[byteInd+1] & 0xFF)));
            }
            aveIntensities.add(total/4000);
            grandTotal += total/4000;
            j ++;
        }
        vidAveAudioIntensity = (double)grandTotal/aveIntensities.size();
    }
 
    /**
     * This method contains an array of frequencies to test scenes for with the calcSceneDCT method allowing us to test 
     * multiple frequencies and change them dynamically
     */
    private void runDCT()
    {
    	int[] freqs = {2000, 4000, 8000, 10000};
    	for (int f: freqs)
    	{
    		calcSceneDCT(f);
    	}
    }
    
    /**
     * This method will test all scenes for the DCT coefficient of the frequency passed in.
     * These frequencies will be stored in the hashmap DCTmap, contained in the Scene object <int frequency, int coefficient>
     * @param freq
     */
    private void calcSceneDCT(int freq)
    {
    	for (Scene s: scenes)
    	{
    		// reset input stream to correct position for this scene
            resetAudioStream(s.getSceneAudioStartPositionBytes()); 
            InputStream inputAudio = dataOnlyAudioStream;
            long pieceLength = s.getSceneAudioEndPositionBytes() - s.getSceneAudioStartPositionBytes() + 1;

            byte[] buffer = new byte[4000];
            long j = 0;
            double total = 0;
            int value;
            while (j < pieceLength) {
            	try {
    				inputAudio.read(buffer);
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
                List<Byte> list = new ArrayList<Byte>();
                for (int byteInd = 0; byteInd < 4000; byteInd+=2) {
                    value =  ((buffer[byteInd] << 8) | (buffer[byteInd+1] & 0xFF));
                    total += Math.abs(Math.cos((freq/2000)*(byteInd/2000.0)*Math.PI*2) * ((double)value));
                }
                j += 4000;
            }
            int coef = (int) (total/pieceLength);
            s.putDCTmap(freq, coef);
    	}
    }
 
    private void findFramesWithZeroAudioIntensities()
    {
    	resetAudioStream(0); 
        InputStream inputAudio = dataOnlyAudioStream;
        byte[] buffer = new byte[4000];
        int j = 0;
        int grandTotal = 0;
        while (j<=rgbErrors.size()) {
            try {
				inputAudio.read(buffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
            int total = 0;
            for (int byteInd = 0; byteInd < 4000; byteInd+=2) {
                total += Math.abs((short) ((buffer[byteInd] << 8) | (buffer[byteInd+1] & 0xFF)));
                if (total <= 0) {
                    isZeroAudioIntensityFrame.add(true);
                    //System.out.println("Found frame with silence: " + j);
                    break;
                } else {
                    isZeroAudioIntensityFrame.add(false);
                    break;
                }

            }

 
            j ++;
        }
    }
    
    private String getRGBErrorVsTimePointsInTabSeparetedFormat() {
        String results = "Error\tTime\n";
        double currentTimeInMillis = 0.0d;
        for (int i = 0; i < rgbErrors.size(); i++) {
            results += rgbErrors.get(i) + "\t" + currentTimeInMillis + "\n";
            // increment time for next frame (each frame adds 1000/24 milliseconds)
            currentTimeInMillis += 1000.0/24.0;
        }

        return results;
    }

    private String getYErrorVsTimePointsInTabSeparetedFormat() {
        String results = "Error\tTime\n";
        double currentTimeInMillis = 0.0d;
        for (int i = 0; i < yErrors.size(); i++) {
            results += yErrors.get(i) + "\t" + currentTimeInMillis + "\n";
            // increment time for next frame (each frame adds 1000/24 milliseconds)
            currentTimeInMillis += 1000.0/24.0;
        }

        return results;
    }

    private String getTotFrameMotionVsTimePointsInTabSeparetedFormat() {
        String results = "Error\tTime\n";
        double currentTimeInMillis = 0.0d;
        for (int i = 0; i < totFrameMotion.size(); i++) {
            results += totFrameMotion.get(i) + "\t" + currentTimeInMillis + "\n";
            // increment time for next frame (each frame adds 1000/24 milliseconds)
            currentTimeInMillis += 1000.0/24.0;
        }

        return results;
    }

     private String getAveIntensitiesInTabSeparetedFormat() {
        String results = "Intensity\tTime\n";
        double currentTimeInMillis = 0.0d;
        for (int i = 0; i < aveIntensities.size(); i++) {
            results += aveIntensities.get(i) + "\t" + currentTimeInMillis + "\n";
            // increment time for next frame (each frame adds 1000/24 milliseconds)
            currentTimeInMillis += 1000.0/24.0;
        }
        return results;
    }

    /**
     * This method goes through RGB error list compilied on consecutive frames
     * and creates shots and scenes based on these errors. The assumptions are
     * as follows: 1) error sum greater than 3,000,000 signifies start of new shot.
     * 2) error greater than 10,000,000 signifies new scene.
     */
    private void createShotsAndScenes() {
        // position tracking vars..
        double currentTimeInMillis = 0.0d;
        long totalBytesPerFrame = numRvalsInFrame + numGvalsInFrame + numBvalsInFrame;
        long shotStartTime = 0;
        long shotEndTime = 0;
        long shotStartVideoPosBytes = 0;
        long shotEndVideoPosBytes = 0;
        long shotStartAudioPosBytes = 0;
        long shotEndAudioPosBytes = 0;
        long sceneStartTime = 0;
        long sceneStartVideoPosBytes = 0;
        long sceneStartAudioPosBytes = 0;
        List<Shot> sceneShots = new ArrayList<Shot>();

        for (int i = 0; i < rgbErrors.size(); i++) {
            int err = rgbErrors.get(i);
            if (i == 0) { // this is very first frame, start new shot
                shotStartTime = 0;
                shotStartVideoPosBytes = 0;
                shotStartAudioPosBytes = 0;
                sceneStartTime = 0;
                sceneStartVideoPosBytes = 0;
                sceneStartAudioPosBytes = 0;

                currentTimeInMillis += 1000.0/24.0;
                shotEndVideoPosBytes += totalBytesPerFrame;
                shotEndAudioPosBytes += 4000; // for each video frame there are 4,000 bytes of audio data
                continue; // nothing to compute, start next iteration
            } 

            if (err >= 10000000 || i == (rgbErrors.size() - 1)) { // new scene or end of video
                // finish shot
                sceneShots.add(new Shot(shotStartVideoPosBytes, shotEndVideoPosBytes - 1, 
                        shotStartAudioPosBytes, shotEndAudioPosBytes - 1, shotStartTime, 
                        Math.round(currentTimeInMillis)));
                shotNum++;

                // create new scene 
                scenes.add(new Scene(sceneStartVideoPosBytes, shotEndVideoPosBytes - 1, 
                        sceneStartAudioPosBytes, shotEndAudioPosBytes - 1, sceneStartTime, 
                        Math.round(currentTimeInMillis), sceneShots));

                // reset variables
                shotStartTime = Math.round(currentTimeInMillis);
                sceneStartTime = Math.round(currentTimeInMillis);
                shotStartVideoPosBytes = shotEndVideoPosBytes;
                shotStartAudioPosBytes = shotEndAudioPosBytes;
                sceneStartVideoPosBytes = shotEndVideoPosBytes;
                sceneStartAudioPosBytes = shotEndAudioPosBytes;
                sceneShots = new ArrayList<Shot>();
                 
            } else if (err >= 3000000) { // new shot
                // finish shot
                sceneShots.add(new Shot(shotStartVideoPosBytes, shotEndVideoPosBytes - 1, 
                        shotStartAudioPosBytes, shotEndAudioPosBytes - 1, shotStartTime, 
                        Math.round(currentTimeInMillis)));
                shotNum++;

                // reset variables
                shotStartTime = Math.round(currentTimeInMillis);
                shotStartVideoPosBytes = shotEndVideoPosBytes;
                shotStartAudioPosBytes = shotEndAudioPosBytes;
            }

            // increment time for next frame (each frame adds 1000/24 milliseconds)
            currentTimeInMillis += 1000.0/24.0;
            shotEndVideoPosBytes += totalBytesPerFrame;
            shotEndAudioPosBytes += 4000; // for each video frame there are 4,000 bytes of audio data
        }

    }

    /**
     * This method goes through Y-channel error list compilied on consecutive frames
     * and creates shots and scenes based on these errors. The assumptions are
     * as follows: 1) error sum greater than 1,000,000 signifies start of new shot.
     * 2) error greater than 1,800,000 signifies new scene.
     */
    private void createShotsAndScenesUsingYChannelErrors() {
        // position tracking vars..
        double currentTimeInMillis = 0.0d;
        long totalBytesPerFrame = numRvalsInFrame + numGvalsInFrame + numBvalsInFrame;
        long shotStartTime = 0;
        long shotEndTime = 0;
        long shotStartVideoPosBytes = 0;
        long shotEndVideoPosBytes = 0;
        long shotStartAudioPosBytes = 0;
        long shotEndAudioPosBytes = 0;
        long sceneStartTime = 0;
        long sceneStartVideoPosBytes = 0;
        long sceneStartAudioPosBytes = 0;
        int numBlackFrames = 0;
        List<Shot> sceneShots = new ArrayList<Shot>();

        for (int i = 0; i < yErrors.size(); i++) {
            int err = yErrors.get(i);

            if (i == 0) { // this is very first frame, start new shot
                shotStartTime = 0;
                shotStartVideoPosBytes = 1;
                shotStartAudioPosBytes = 0;
                sceneStartTime = 0;
                sceneStartVideoPosBytes = 1;
                sceneStartAudioPosBytes = 0;

                currentTimeInMillis += 1000.0/24.0;
                shotEndVideoPosBytes += totalBytesPerFrame;
                shotEndAudioPosBytes += 4000; // for each video frame there are 4,000 bytes of audio data
                if (isBlackFrame.get(i) == true) {
                    numBlackFrames++;
                }
                continue; // nothing to compute, start next iteration
            } 

            if (err >= 5000000 || i == (yErrors.size() - 1)) { // new scene or end of video
                // finish shot
                Shot s = new Shot(shotStartVideoPosBytes, shotEndVideoPosBytes - 1,
                        shotStartAudioPosBytes, shotEndAudioPosBytes - 1, shotStartTime,
                        Math.round(currentTimeInMillis));

                s.setNumOfBlackFrames(numBlackFrames);
                s.setAveMotionPerSec(calcAveMotionPerSecond((int)shotStartTime, (int)Math.round(currentTimeInMillis)));
                sceneShots.add(s);
                shotNum++;

                // create new scene 
                Scene sc = new Scene(sceneStartVideoPosBytes, shotEndVideoPosBytes - 1,
                        sceneStartAudioPosBytes, shotEndAudioPosBytes - 1, sceneStartTime,
                        Math.round(currentTimeInMillis), sceneShots);
                sc.setAveMotionPerSec(calcAveMotionPerSecond((int)sceneStartTime, (int)Math.round(currentTimeInMillis)));
                sc.setHasSilence(checkIntervalForSilence((int)sceneStartTime, (int)Math.round(currentTimeInMillis)));
                scenes.add(sc);

                // reset variables
                shotStartTime = Math.round(currentTimeInMillis);
                sceneStartTime = Math.round(currentTimeInMillis);
                shotStartVideoPosBytes = shotEndVideoPosBytes;
                shotStartAudioPosBytes = shotEndAudioPosBytes;
                sceneStartVideoPosBytes = shotEndVideoPosBytes;
                sceneStartAudioPosBytes = shotEndAudioPosBytes;
                numBlackFrames = 0;
                sceneShots = new ArrayList<Shot>();
                 
            } else if (err >= 1000000) { // new shot
                // finish shot
                Shot s = new Shot(shotStartVideoPosBytes, shotEndVideoPosBytes - 1,
                        shotStartAudioPosBytes, shotEndAudioPosBytes - 1, shotStartTime,
                        Math.round(currentTimeInMillis));

                s.setNumOfBlackFrames(numBlackFrames);
                s.setAveMotionPerSec(calcAveMotionPerSecond((int)shotStartTime, (int)Math.round(currentTimeInMillis)));
                sceneShots.add(s);
                shotNum++;

                // reset variables
                shotStartTime = Math.round(currentTimeInMillis);
                shotStartVideoPosBytes = shotEndVideoPosBytes;
                shotStartAudioPosBytes = shotEndAudioPosBytes;
                numBlackFrames = 0;
            }

            // increment time for next frame (each frame adds 1000/24 milliseconds)
            if (isBlackFrame.get(i) == true) {
                numBlackFrames++;
            }
            currentTimeInMillis += 1000.0/24.0;
            shotEndVideoPosBytes += totalBytesPerFrame;
            shotEndAudioPosBytes += 4000; // for each video frame there are 4,000 bytes of audio data
        }

        // calc ave. motion for whole video
        aveMotion = calcAveMotionPerSecond(0, (int) (1000.0/24.0 * yErrors.size()));
    }

    /**
     * This method uses instance variable name "totFrameMotion" together with 
     * shot/scene start and end time to calculate average motion vector value
     * per second for respective shot/scene.
     */
    private int calcAveMotionPerSecond(int startTimeInMillisecs, int endTimeInMillisecs) {
        int startIndex = (int) Math.round((double) startTimeInMillisecs/(1000.0/24.0));
        int windowSize = (int) Math.round((double) (endTimeInMillisecs-startTimeInMillisecs)/(1000.0/24.0));

        int motionSum = 0;
        for (int i = startIndex; i < (startIndex + windowSize); i++) {
//if (i == startIndex) { System.out.println("Key Frame Motion: " + totFrameMotion.get(i)); }
//        for (int i = (startIndex+1); i < (startIndex + windowSize); i++) { // always skip key frame
            if (totFrameMotion.get(i) > 1000) {
                ;
            } else if (totFrameMotion.get(i) > 600) { // motion above 100 indicates new keyframe, don't take it into account...
                motionSum += 600;
            } else {
                motionSum += totFrameMotion.get(i);
            }
        }

        int aveMotionPerSecond = (int) Math.round((double) motionSum / ((double) windowSize * (1.0/24.0)));
        return aveMotionPerSecond;
    }

    /**
     * Checks to see if there are at least 3 frames of silence within specified timeframe.
     */
    private boolean checkIntervalForSilence(int startTimeInMillisecs, int endTimeInMillisecs) {
        int startIndex = (int) Math.round((double) startTimeInMillisecs/(1000.0/24.0));
        int windowSize = (int) Math.round((double) (endTimeInMillisecs-startTimeInMillisecs)/(1000.0/24.0));

        int numSilenceFrames = 0;
        for (int i = startIndex; i < (startIndex + windowSize); i++) {
            if (isZeroAudioIntensityFrame.get(i)) {
                numSilenceFrames++;
            }
        }

        return (numSilenceFrames >= 3);
    }



    /**
     * This method writes out all scenes in "scenes" instance variable
     * to output files specified during program creation. Assumes
     * scenes are ordered chronologically.
     */
    private void writeScenesToDisk() {
        // create new output files
        FileOutputStream osVid = null;

        try {
            // for video
            File file = new File(videoOutFileName);
            osVid = new FileOutputStream(file);

            // for audio
            file = new File(audioOutFileName);
            List<Byte> soundBytes = new ArrayList<Byte>(); // this will hold sound data. wav files are pretty small so data can be used directly

            byte[] buffer = new byte[4096];
            for (int i = 0; i < scenes.size(); i++) {
            	if (!scenes.get(i).isAd())
            	{
	                /******************************/
	                /*** write video to output  ***/
	                /******************************/
	
	                // reset input stream to correct position for this scene
                    resetVideoStream(scenes.get(i).getSceneVideoStartPositionBytes()); 
                    long pieceLength = scenes.get(i).getSceneVideoEndPositionBytes() - scenes.get(i).getSceneVideoStartPositionBytes() + 1;

	                int j = 0;
	                while (j < pieceLength) {
	                    if ((pieceLength - j) > 4096) { // use buffer for speed
	                        inV.read(buffer);
	                        osVid.write(buffer);
	                        j += 4096;
	                    } else {
	                        osVid.write(inV.read());
	                        j++;
	                    }
	                }
	
	
	                /*****************************/
	                /*** write audio to stream ***/
	                /*****************************/
	
	                // reset input stream to correct position for this scene
	                resetAudioStream(scenes.get(i).getSceneAudioStartPositionBytes()); 
	                InputStream inputAudio = dataOnlyAudioStream;
	                pieceLength = scenes.get(i).getSceneAudioEndPositionBytes() - scenes.get(i).getSceneAudioStartPositionBytes() + 1;
	
	                j = 0;
	                while (j < pieceLength) {
	                    if ((pieceLength - j) > 4096) { // use buffer for speed
	                        inputAudio.read(buffer);
	                        List<Byte> list = new ArrayList<Byte>();
	                        for (byte value : buffer) {
	                            list.add(value);
	                        }
	                        soundBytes.addAll(list);
	                        j += 4096;
	                    } else {
	                        byte[] val = new byte[1];
	                        inputAudio.read(val);
	                        soundBytes.add(val[0]);
	                        j++;
	                    }
	                }
            	}
            }

            // write audio stream created above to wav file on disk
            byte[] soundArr = new byte[soundBytes.size()];
            for (int i = 0; i < soundBytes.size(); i++) {
                soundArr[i] = soundBytes.get(i);
            }
            InputStream inStream = new ByteArrayInputStream(soundArr);
            AudioFormat format = playSound.getAudioFormat();
            AudioInputStream stream = new AudioInputStream(inStream, format, soundArr.length);
            AudioSystem.write(stream, Type.WAVE, file);

            if (osVid != null) {
                osVid.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method writes out all scenes in "scenes" local variable
     * to output files specified during program creation. Assumes
     * scenes are ordered chronologically.
     */
    private void writeScenesToDisk(List<Scene> scenes) {
        // create new output files
        FileOutputStream osVid = null;
        int totNumFrames = 0;

        try {
            // for video
            File file = new File(videoOutFileName);
            osVid = new FileOutputStream(file);

            // for audio
            file = new File(audioOutFileName);
            List<Byte> soundBytes = new ArrayList<Byte>(); // this will hold sound data. wav files are pretty small so data can be used directly

            byte[] buffer = new byte[4096];
            for (int i = 0; i < scenes.size(); i++) {
                totNumFrames += scenes.get(i).getNumOfFrames();
//            	if (!scenes.get(i).isAd())
//            	{
	                /******************************/
	                /*** write video to output  ***/
	                /******************************/
	
	                // reset input stream to correct position for this scene
	                resetVideoStream(scenes.get(i).getSceneVideoStartPositionBytes()); 
	                long pieceLength = scenes.get(i).getSceneVideoEndPositionBytes() - scenes.get(i).getSceneVideoStartPositionBytes() + 1;
	
	                int j = 0;
	                while (j < pieceLength) {
	                    if ((pieceLength - j) > 4096) { // use buffer for speed
	                        inV.read(buffer);
	                        osVid.write(buffer);
	                        j += 4096;
	                    } else {
	                        osVid.write(inV.read());
	                        j++;
	                    }
	                }
	
	
	                /*****************************/
	                /*** write audio to stream ***/
	                /*****************************/
	
	                // reset input stream to correct position for this scene
	                resetAudioStream(scenes.get(i).getSceneAudioStartPositionBytes()); 
	                InputStream inputAudio = dataOnlyAudioStream;
	                pieceLength = scenes.get(i).getSceneAudioEndPositionBytes() - scenes.get(i).getSceneAudioStartPositionBytes() + 1;
	
	                j = 0;
	                while (j < pieceLength) {
	                    if ((pieceLength - j) > 4096) { // use buffer for speed
	                        inputAudio.read(buffer);
	                        List<Byte> list = new ArrayList<Byte>();
	                        for (byte value : buffer) {
	                            list.add(value);
	                        }
	                        soundBytes.addAll(list);
	                        j += 4096;
	                    } else {
	                        byte[] val = new byte[1];
	                        inputAudio.read(val);
	                        soundBytes.add(val[0]);
	                        j++;
	                    }
	                }
//            	}
            }

            // write audio stream created above to wav file on disk
            byte[] soundArr = new byte[soundBytes.size()];
            for (int i = 0; i < soundBytes.size(); i++) {
                soundArr[i] = soundBytes.get(i);
            }
            InputStream inStream = new ByteArrayInputStream(soundArr);
            AudioFormat format = playSound.getAudioFormat();
            AudioInputStream stream = new AudioInputStream(inStream, format, soundArr.length);
            AudioSystem.write(stream, Type.WAVE, file);

            if (osVid != null) {
                osVid.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method writes out all scenes in "scenes" instance variable
     * that are marked as advertisement to output files specified 
     * during program creation. 
     *
     * Assumes scenes are ordered chronologically.
     *
     */
    private void writeAdScenesToDisk() {
        // create new output files
        FileOutputStream osVid = null;

        try {
            // for video
            File file = new File(videoOutFileName);
            osVid = new FileOutputStream(file);

            // for audio
            file = new File(audioOutFileName);
            List<Byte> soundBytes = new ArrayList<Byte>(); // this will hold sound data. wav files are pretty small so data can be used directly

            byte[] buffer = new byte[4096];
            for (int i = 0; i < scenes.size(); i++) {
            	if (scenes.get(i).isAd())
            	{
	                /******************************/
	                /*** write video to output  ***/
	                /******************************/
	
	                // reset input stream to correct position for this scene
	                resetVideoStream(scenes.get(i).getSceneVideoStartPositionBytes()); 
	                long pieceLength = scenes.get(i).getSceneVideoEndPositionBytes() - scenes.get(i).getSceneVideoStartPositionBytes() + 1;
	
	                int j = 0;
	                while (j < pieceLength) {
	                    if ((pieceLength - j) > 4096) { // use buffer for speed
	                        inV.read(buffer);
	                        osVid.write(buffer);
	                        j += 4096;
	                    } else {
	                        osVid.write(inV.read());
	                        j++;
	                    }
	                }
	
	
	                /*****************************/
	                /*** write audio to stream ***/
	                /*****************************/
	
	                // reset input stream to correct position for this scene
	                resetAudioStream(scenes.get(i).getSceneAudioStartPositionBytes()); 
	                InputStream inputAudio = dataOnlyAudioStream;
	                pieceLength = scenes.get(i).getSceneAudioEndPositionBytes() - scenes.get(i).getSceneAudioStartPositionBytes() + 1;
	
	                j = 0;
	                while (j < pieceLength) {
	                    if ((pieceLength - j) > 4096) { // use buffer for speed
	                        inputAudio.read(buffer);
	                        List<Byte> list = new ArrayList<Byte>();
	                        for (byte value : buffer) {
	                            list.add(value);
	                        }
	                        soundBytes.addAll(list);
	                        j += 4096;
	                    } else {
	                        byte[] val = new byte[1];
	                        inputAudio.read(val);
	                        soundBytes.add(val[0]);
	                        j++;
	                    }
	                }
            	}
            }

            // write audio stream created above to wav file on disk
            byte[] soundArr = new byte[soundBytes.size()];
            for (int i = 0; i < soundBytes.size(); i++) {
                soundArr[i] = soundBytes.get(i);
            }
            InputStream inStream = new ByteArrayInputStream(soundArr);
            AudioFormat format = playSound.getAudioFormat();
            AudioInputStream stream = new AudioInputStream(inStream, format, soundArr.length);
            AudioSystem.write(stream, Type.WAVE, file);

            if (osVid != null) {
                osVid.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private void resetVideoStream(long newPos) {
        synchronized(threadLock) {
            try{
                if (newPos > 0) {
                    File file = new File(videoFileName);
                    //inV = new BufferedInputStream(new FileInputStream(file));
                    inV = new FileInputStream(file);
                    inV.mark((int)file.length());
                    mySkip(inV, newPos);
                }
            } catch (IOException e) {
                System.err.println("Unable to reset video stream during rewind operation: " + e);
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private void resetAudioStream(long newPos) {
        synchronized(threadLock) {
            try {
                playSound.closeLine();
                playSound = null;
                File file = new File(audioFileName);
                inA = new FileInputStream(file);
                playSound = new PlaySound(inA);
                playSound.initSound();
                playSound.skipNBytes(newPos);
                dataOnlyAudioStream = playSound.getAudioInputStream();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } catch (PlayWaveException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    public static void mySkip(InputStream is, long n) throws IOException {
        while(n > 0) {
            long n1 = is.skip(n);
            if( n1 > 0 ) {
                n -= n1;
            } else if( n1 == 0 ) { // should we retry? lets read one byte
                if( is.read() == -1)  // EOF
                    break;
                else 
                    n--;
            } else // negative? this should never happen but...
                throw new IOException("skip() returned a negative value - this should never happen");
        }
    }
    
    /**
     * This Method goes through each scene and finds the average R, G, and B values 
     * of each of its frames and then averages them in order to find the scenes average
     * {R,G,B} tuple.
     * 
     * In addition, this also finds the video's average R, G, and B values
     * 
     */
    public void scoreAverageRGB()
    {
    	for (Scene s : scenes)
    	{
    		//Calculate average RGB values per scene
    		double totalSceneRVal = 0;
    		double totalSceneGVal = 0;
    		double totalSceneBVal = 0;
    		
    		for (long f = (s.getSceneVideoStartPositionBytes()/(numRvalsInFrame*3)); f<= (s.getSceneVideoEndPositionBytes()/(numRvalsInFrame*3)); f++)
    		{
    			totalSceneRVal += aveRVals.get((int) f);
    			totalSceneGVal += aveGVals.get((int) f);
    			totalSceneBVal += aveRVals.get((int) f);
    		
    		}
    		
    		vidAveRVal += totalSceneRVal;
    		vidAveGVal += totalSceneGVal;
    		vidAveBVal += totalSceneBVal;
    		
    		totalSceneRVal /= ((s.getSceneVideoEndPositionBytes()-s.getSceneVideoStartPositionBytes())/(3*numRvalsInFrame)+1);
    		totalSceneGVal /= ((s.getSceneVideoEndPositionBytes()-s.getSceneVideoStartPositionBytes())/(3*numRvalsInFrame)+1);
    		totalSceneBVal /= ((s.getSceneVideoEndPositionBytes()-s.getSceneVideoStartPositionBytes())/(3*numRvalsInFrame)+1);
    		
    		s.setAveRGB(totalSceneRVal, totalSceneGVal, totalSceneBVal);
    	}
		    vidAveRVal /= aveRVals.size();
			vidAveGVal /= aveGVals.size();
			vidAveBVal /= aveBVals.size();
    }
  
    /**
     * This Method calculates the average Audio Intensity for each scene
     */
    public void scoreAverageAudioIntensity()
    {
    	for (Scene s: scenes)
    	{
    		double totalIntensity = 0;
    		
    		for (long f = (s.getSceneAudioStartPositionBytes()/4000); f <= (s.getSceneAudioEndPositionBytes()/4000); f++)
    		{
    			totalIntensity += aveIntensities.get((int) f);
    		}
    		
    		s.setAveAudioIntensity(totalIntensity/((s.getSceneAudioEndPositionBytes()-s.getSceneAudioStartPositionBytes())/4000));
    	}
    }
    
    /**
     * This method will go through each scene and, depending on the values of its differently
     * values metrics, set whether the scene is classified as an Ad scene.
     */
    public void chooseAds()
    { 	
    	// Metric Weights
    	double RGBWeight = 0.1;
    	double shotPSceneWeight = 0.2;
    	double sceneLengthWeight = 0.1;
        double motionVectorWeight = 0.5;
    	
    	// Whole Video Statistics
    	double aveSceneLength = (double) rgbErrors.size()/ (double) scenes.size();
    	double aveShotPerScene = (double) shotNum/ (double) scenes.size();
    	double aveShotPerSec = (double) shotNum/ (double) (rgbErrors.size()/24.0);
    	double aveMotionPerSec = aveMotion;
    	
System.out.println("Ave Sc. Length: " + aveSceneLength);
System.out.println("Ave Shot per Scene: " + aveShotPerScene);
System.out.println("Ave Shot per Second: " + aveShotPerSec);
System.out.println("Ave Motion per Second: " + aveMotionPerSec);

    	// Scene Deviation values
    	double RDev, GDev, BDev, shotPerSecDev, shotPerSceneDev, sceneLengthDev, aveIntensityDev;
    	for(Scene s: scenes)
    	{
    		RDev = Math.abs(s.getAveRGB()[0]-vidAveRVal)/vidAveRVal;
    		GDev = Math.abs(s.getAveRGB()[1]-vidAveGVal)/vidAveGVal;
    		BDev = Math.abs(s.getAveRGB()[2]-vidAveBVal)/vidAveBVal;
    		shotPerSecDev = Math.abs(s.getShotPerSec()-aveShotPerSec);
    		shotPerSceneDev = Math.abs((double) s.shotNum()-aveShotPerScene)/aveShotPerScene;
    		sceneLengthDev = Math.abs(((double) s.getByteLength()/ (double) numRvalsInFrame)+1-aveSceneLength)/aveSceneLength;
    		aveIntensityDev = Math.abs((double) s.getAveAudioIntensity()-vidAveAudioIntensity);
    		
    		if (((RDev + GDev + BDev) * RGBWeight + 
    			(shotPerSceneDev* shotPSceneWeight) +
    			(shotPerSecDev* shotPSceneWeight) +
    			(sceneLengthDev * sceneLengthWeight))>.5) //As of now, .5 is just an arbitrary threshold
    			
    		{
    			s.setAd(true);
    		}
    	}
    }

    private int calcTotalMotionForTwoFrames(byte[] bytesYPrevFrame, byte[] bytesY) {
        // convert frame buffers to double arrays for easier manipulation
        int ind = 0;
        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                prevBytesY[x][y] = bytesYPrevFrame[ind];
                currBytesY[x][y] = bytesYPrevFrame[ind];
                ind++;
            }
        }

        List<MotionVector> motionVectors = new ArrayList<MotionVector>();

        // now traverse each block and find smallest MAD
        for(int y = 0; y < height; y += BLOCK_SIZE){
            for(int x = 0; x < width; x += BLOCK_SIZE){
                double blockMAD = -1.0;
                MotionVector v = null;
                // for each block, find lowest MAD
                for(int i = -K; i < K; i++){
                    for(int j = -K; j < K; j++){
                        int madSum = 0;
                        for(int k = 0; k < BLOCK_SIZE; k++){
                            for(int l = 0; l < BLOCK_SIZE; l++){
                                int p = x + l;
                                int q = y + k;

                                if (p < 0 || q < 0 || p >= width || q >= height || (p + i) < 0 || (p + i) >= width || (q + j) < 0 || (q + j) >= height) {
                                    madSum += 10000000;
                                } else {
                                    madSum += Math.abs(currBytesY[p][q] - prevBytesY[p + i][q + j]);
                                }
                            }
                        }
                        double mad = (double) madSum / (double) (BLOCK_SIZE * BLOCK_SIZE);
                        if (blockMAD == -1.0 || mad < blockMAD) {
                            blockMAD = mad;
                            v = new MotionVector(i, j);
                        }
                    }
                }
                // store motion vector for this block into a list
                if (v != null) {
                    motionVectors.add(v);
                }
            }
        }

        int totalMotion = 0;
        for (MotionVector v : motionVectors) {
            totalMotion += v.getTotalMotionValue();
        }

        return totalMotion;
    }

    private void chooseAdsUsingPointSystem() {
    for (int i = 0; i < aveIntensities.size(); i++) {
        if ((aveIntensities.get(i) <= 0) || ((double) aveIntensities.get(i)/vidAveAudioIntensity >= 1.5) 
                || ((double) aveIntensities.get(i)/vidAveAudioIntensity <= 0.5)) {
            this.numOfLowIntensityFrames++;
        }
    }

    for (Scene s : scenes) {
        // traverse ave audio intensity values and search for zeros
        intensityLoop:
        for (int i = 0; i < aveIntensities.size(); i++) {
            //if (aveIntensities.get(i) <= 0) {
            if ((aveIntensities.get(i) <= 0) || ((double) aveIntensities.get(i)/vidAveAudioIntensity >= 1.5) 
                    || ((double) aveIntensities.get(i)/vidAveAudioIntensity <= 0.5)) {
                int intervalStartTime = 0;
                int intervalEndTime = 0;
                intervalStartTime = (int) Math.round(i*(1000.0/24.0) - 5000);
                intervalEndTime = (int) Math.round(i*(1000.0/24.0) + 5000);
                
                // add 1 point to scene's score if it starts within 500ms of zero intensity frame
                if (s.getSceneStartPositionInMillis() < intervalEndTime && s.getSceneStartPositionInMillis() > intervalStartTime && s.getLengthInSeconds() <= 40.0) {
                    /*
                    System.out.println("Found Scene (starts at): " + s.getSceneStartPositionInMillis()/1000.0);
                    System.out.println("Length: " + s.getLengthInSeconds() + "\n\n\n");
                    */

                    s.addScorePoints(1);
                    break intensityLoop;

                }

/*
                System.out.println("Frame Time: " + ((double) i*(1000.0/24.0)/1000.0));
                System.out.println("From Interval: " + ((i*(1000.0/24.0)/1000.0) - 1));
                System.out.println("To Interval: " + ((i*(1000.0/24.0)/1000.0) + 1) + "\n\n\n");
*/

            }

            

        }


        boolean startWithinInterval = false;
        boolean endWithinInterval = false;
        for (int i = 0; i < aveIntensities.size(); i++) {
            //if (aveIntensities.get(i) <= 0) {
            if ((aveIntensities.get(i) <= 0) || ((double) aveIntensities.get(i)/vidAveAudioIntensity >= 1.5) 
                    || ((double) aveIntensities.get(i)/vidAveAudioIntensity <= 0.5)) {
                int intervalStartTime = 0;
                int intervalEndTime = 0;
                int iUsedForStart = 0;
//                int iUsedForEnd = 0;
                intervalStartTime = (int) Math.round(i*(1000.0/24.0) - 15000);
                intervalEndTime = (int) Math.round(i*(1000.0/24.0) + 15000);

                // add 1 point to scene's score if it starts within 500ms of zero intensity frame
                if (s.getSceneStartPositionInMillis() < intervalEndTime 
                        && s.getSceneStartPositionInMillis() > intervalStartTime 
                        && s.getLengthInSeconds() <= 40.0) {

                    startWithinInterval = true;
//                    iUsedForStart = i;
                    /*
                       System.out.println("Found Scene (starts at): " + s.getSceneStartPositionInMillis()/1000.0);
                       System.out.println("Length: " + s.getLengthInSeconds() + "\n\n\n");
                       s.addScorePoints(1);
                       break intensityLoop;
                     */
                }

                if (s.getSceneEndPositionInMillis() < intervalEndTime 
                        && s.getSceneEndPositionInMillis() > intervalStartTime 
                        && s.getLengthInSeconds() <= 40.0) {
//                    if(iUsedForStart != i) {
                    endWithinInterval = true;
//                    }

                }
            }

        }
        if (startWithinInterval && endWithinInterval) {
            s.addScorePoints(1);
        }

        double aveShotPerSec = (double) shotNum/ (double) (rgbErrors.size()/24.0);
        if((s.getShotPerSec()/aveShotPerSec) >= 1.1 && s.getLengthInSeconds() <= 40.0) {
            s.addScorePoints(2);
        }

        if ((((double) s.getAveMotionPerSecond() / (double) aveMotion) >= 1.05) 
                && s.getLengthInSeconds() <= 40.0) {
            s.addScorePoints(3);
        }


    }

    
    for (Scene s : scenes) {
        if (s.getScorePoints() > 2) {
            s.setAd(true);
        }

    }


    }

    private void postProcessAds() {
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (s.isAd()) { // if this is an ad search forward and if another ad or intensity drop is found within 5 seconds, make all scenes in between ads as well

                double totalSizeOfAdBlock = s.getLengthInMillis();
                int j = i + 1; // start from very next scene
                long currTimePositionMillis = s.getSceneStartPositionInMillis();

                sceneLoop:
                while (currTimePositionMillis < (s.getSceneStartPositionInMillis() + 20000)) {
                    if (j < scenes.size()) {
                        totalSizeOfAdBlock += scenes.get(j).getLengthInMillis();
                        if (scenes.get(j).isAd() 
                            && totalSizeOfAdBlock < 30000 
                            && scenes.get(j).getLengthInSeconds() < 30.0) {

                            for (int k = i+1; k < j; k++) {
                                scenes.get(k).setAd(true);

                                if (k - 1 >= 0) {
                                    if (scenes.get(k-1).getScorePoints() > scenes.get(k).getScorePoints()) {
                                        scenes.get(k).addScorePoints(1);
                                    }
                                } else if (k + 1 < scenes.size()) {
                                    if (scenes.get(k+1).getScorePoints() > scenes.get(k).getScorePoints()) {
                                        scenes.get(k).addScorePoints(1);
                                    }
                                }
                            }
                            i = j - 1;
                            break sceneLoop;
                        }

                        currTimePositionMillis = scenes.get(j).getSceneStartPositionInMillis();
                        j++;
                    } else {
                        break sceneLoop;
                    }
                }
            }
        }


        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (s.isAd()) { // find if there is low intensity frame within 5 seconds of this ad scene; if so, fill in the blanks

                long searchStart = s.getSceneAudioEndPositionBytes()/4000;
                long searchStop  = searchStart + (long) Math.round(10000.0/(1000.0/24.0));
                long lowIntensityAudioBytePosition = 0;
                boolean foundLowIntensityFrame = false;

                for (int j = (int) searchStart; j <= searchStop; j++) {
            //        if (isZeroAudioIntensityFrame.get(j)) {

                    if (j < aveIntensities.size()) {
                        if ((aveIntensities.get(j) <= 0) || ((double) aveIntensities.get(j)/vidAveAudioIntensity >= 1.5) 
                                || ((double) aveIntensities.get(j)/vidAveAudioIntensity <= 0.5)) {
                            foundLowIntensityFrame = true;

                            // calculate end scene location
                            lowIntensityAudioBytePosition = 4000*j;
                        }
                    }
                }

                int maxInclusiveScene = 0;
                if (foundLowIntensityFrame) {
                    for (int j = i+1; j < scenes.size(); j++) {
                        if (scenes.get(j).getSceneAudioEndPositionBytes() > lowIntensityAudioBytePosition) {

                            maxInclusiveScene = j;
                            break;
                        }
                    }
                }

                // now add these scenes to ad block
                double totalAdditionalLength = scenes.get(i).getLengthInSeconds();
                for (int k = i+1; k <= maxInclusiveScene; k++) {
                    totalAdditionalLength += scenes.get(k).getLengthInSeconds();
                    if (k == maxInclusiveScene && totalAdditionalLength > 15.0) {
                        continue;
                    } else {
                        scenes.get(k).setAd(true);
                                if (k - 1 >= 0) {
                                    if (scenes.get(k-1).getScorePoints() > scenes.get(k).getScorePoints()) {
                                        scenes.get(k).addScorePoints(1);
                                    }
                                } else if (k + 1 < scenes.size()) {
                                    if (scenes.get(k+1).getScorePoints() > scenes.get(k).getScorePoints()) {
                                        scenes.get(k).addScorePoints(1);
                                    }
                                }

                    }
                }


            }
        }

        // now we should have all ads clustered together into blocks
        // non-ad scenes mean start of new block

        // go through each ad block and check if it's > 45 seconds long
        // if it is, remove lowest ranked scenes from ends until it's 
        // smaller than 45 seconds

        int blockCounter = 1;
        int startSceneNum = -1;
        int endSceneNum = -1;
        boolean sawStart = false;
        boolean sawEnd = false;
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (!sawStart && s.isAd()) { // find if there is low intensity frame within 5 seconds of this ad scene; if so, fill in the blanks
                sawStart = true;
                startSceneNum = i;
            } else if (sawStart && !s.isAd()) {
                sawStart = false;
                endSceneNum = i-1;

/*
System.err.println("AD BLOCK NUM: " + blockCounter);
System.err.println("Block Start Time: " + (double)scenes.get(startSceneNum).getSceneStartPositionInMillis()/1000.0);
System.err.println("Block End Time: " + (double)scenes.get(endSceneNum).getSceneEndPositionInMillis()/1000.0 + "\n\n\n");
*/

trimAdBlock(startSceneNum, endSceneNum);

                blockCounter++;
            } else if (sawStart && (i+1 == scenes.size())) {
                sawStart = false;
                endSceneNum = i;
/*
System.err.println("AD BLOCK NUM: " + blockCounter);
System.err.println("Block Start Time: " + (double)scenes.get(startSceneNum).getSceneStartPositionInMillis()/1000.0);
System.err.println("Block End Time: " + (double)scenes.get(endSceneNum).getSceneEndPositionInMillis()/1000.0 + "\n\n\n");
*/

trimAdBlock(startSceneNum, endSceneNum);

                blockCounter++;
                 
            }
        }

        /* Finally, delete any blocks smaller than 25 seconds */
        int blockCounter2 = 1;
        startSceneNum = -1;
        endSceneNum = -1;
        sawStart = false;
        sawEnd = false;
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (!sawStart && s.isAd()) { // find if there is low intensity frame within 5 seconds of this ad scene; if so, fill in the blanks
                sawStart = true;
                startSceneNum = i;
            } else if (sawStart && !s.isAd()) {
                sawStart = false;
                endSceneNum = i-1;

                blockCounter2++;
            } else if (sawStart && (i+1 == scenes.size())) {
                sawStart = false;
                endSceneNum = i;


                blockCounter2++;
            }
        }

        boolean deleteSmallBlocks = true;
        if (blockCounter == blockCounter2) {
            deleteSmallBlocks = false;
        }
        
        /* Finally, delete any blocks smaller than 25 seconds */
        blockCounter = 1;
        startSceneNum = -1;
        endSceneNum = -1;
        sawStart = false;
        sawEnd = false;
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (!sawStart && s.isAd()) { // find if there is low intensity frame within 5 seconds of this ad scene; if so, fill in the blanks
                sawStart = true;
                startSceneNum = i;
            } else if (sawStart && !s.isAd()) {
                sawStart = false;
                endSceneNum = i-1;


                if (deleteSmallBlocks && ((scenes.get(endSceneNum).getSceneEndPositionInMillis() - scenes.get(startSceneNum).getSceneStartPositionInMillis()) < 26000)) {
                    for (int k = startSceneNum; k <= endSceneNum; k++) {
                        scenes.get(k).setAd(false);
                    }
                }

                blockCounter++;
            } else if (sawStart && (i+1 == scenes.size())) {
                sawStart = false;
                endSceneNum = i;


                if (deleteSmallBlocks && ((scenes.get(endSceneNum).getSceneEndPositionInMillis() - scenes.get(startSceneNum).getSceneStartPositionInMillis()) < 26000)) {
                    for (int k = startSceneNum; k <= endSceneNum; k++) {
                        scenes.get(k).setAd(false);
                    }
                }

                blockCounter++;
                 
            }
        }


        blockCounter = 1;
        startSceneNum = -1;
        endSceneNum = -1;
        sawStart = false;
        sawEnd = false;
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (!sawStart && s.isAd()) { // find if there is low intensity frame within 5 seconds of this ad scene; if so, fill in the blanks
                sawStart = true;
                startSceneNum = i;
            } else if (sawStart && !s.isAd()) {
                sawStart = false;
                endSceneNum = i-1;


                boolean smallScore = true;
                for (int k = startSceneNum; k <= endSceneNum; k++) {
                    if (scenes.get(k).getScorePoints() > 2) {
                        smallScore = false;
                    }
                }

                if (smallScore) {
                    for (int k = startSceneNum; k <= endSceneNum; k++) {
                        scenes.get(k).setAd(false);
                    }
                }

                blockCounter++;
            } else if (sawStart && (i+1 == scenes.size())) {
                sawStart = false;
                endSceneNum = i;


                boolean smallScore = true;
                for (int k = startSceneNum; k <= endSceneNum; k++) {
                    if (scenes.get(k).getScorePoints() > 2) {
                        smallScore = false;
                    }
                }

                if (smallScore) {
                    for (int k = startSceneNum; k <= endSceneNum; k++) {
                        scenes.get(k).setAd(false);
                    }
                }

                blockCounter++;
                 
            }
        }

        blockCounter = 1;
        startSceneNum = -1;
        endSceneNum = -1;
        sawStart = false;
        sawEnd = false;
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            if (!sawStart && s.isAd()) { // find if there is low intensity frame within 5 seconds of this ad scene; if so, fill in the blanks
                sawStart = true;
                startSceneNum = i;
            } else if (sawStart && !s.isAd()) {
                sawStart = false;
                endSceneNum = i-1;
/*
System.err.println("AD BLOCK NUM: " + blockCounter);
System.err.println("Block Start Time: " + (double)scenes.get(startSceneNum).getSceneStartPositionInMillis()/1000.0);
System.err.println("Block End Time: " + (double)scenes.get(endSceneNum).getSceneEndPositionInMillis()/1000.0 + "\n\n\n");
*/

                blockCounter++;
            } else if (sawStart && (i+1 == scenes.size())) {
                sawStart = false;
                endSceneNum = i;

/*
System.err.println("AD BLOCK NUM: " + blockCounter);
System.err.println("Block Start Time: " + (double)scenes.get(startSceneNum).getSceneStartPositionInMillis()/1000.0);
System.err.println("Block End Time: " + (double)scenes.get(endSceneNum).getSceneEndPositionInMillis()/1000.0 + "\n\n\n");
*/

                blockCounter++;
                 
            }
        }

    }


    private void trimAdBlock(int startScene, int endScene) {

        if((scenes.get(endScene).getSceneEndPositionInMillis() - scenes.get(startScene).getSceneStartPositionInMillis()) > 61000) {
            double temp = (double) (endScene - startScene) / 2.0;
            trimAdBlock(startScene, startScene + (int) temp);
            trimAdBlock(startScene + (int) temp + 1, endScene);
            return;
        }

        long searchStart = scenes.get(startScene).getSceneAudioEndPositionBytes()/4000;
        long searchStop  = scenes.get(endScene).getSceneAudioEndPositionBytes()/4000;
        long lowIntensityAudioBytePosition = 0;
        boolean foundLowIntensityFrame = false;

        forLoop:
        for (int j = (int) searchStart; j <= searchStop; j++) {
            //        if (isZeroAudioIntensityFrame.get(j)) {

            if (j < aveIntensities.size()) {
                if ((aveIntensities.get(j) <= 0) || ((double) aveIntensities.get(j)/vidAveAudioIntensity >= 1.5) 
                        || ((double) aveIntensities.get(j)/vidAveAudioIntensity <= 0.5)) {
                    foundLowIntensityFrame = true;

                    // calculate end scene location
                    lowIntensityAudioBytePosition = 4000*j;
                    break forLoop;
                }
            }
        }

        int maxInclusiveScene = 0;
        if (foundLowIntensityFrame) {
            for (int j = startScene; j <= endScene; j++) {
                if (scenes.get(j).getSceneAudioEndPositionBytes() > lowIntensityAudioBytePosition) {
                    maxInclusiveScene = j;
                    break;
                }
            }
        }


        double aveShotPerSec = (double) shotNum/ (double) (rgbErrors.size()/24.0);
        // go front to back and delete everything before first with motion or low intensity
        for (int i = startScene; i <= endScene; i++) {
            Scene s = scenes.get(i);

            // search scene for low intensity
            if (i == maxInclusiveScene && this.numOfLowIntensityFrames < 300) {
                break;
            }

            // make sure it doesn't comply with motion and scenePerSec thresholds
            if ( s.getScorePoints() < 3 &&
                    !((s.getShotPerSec()/aveShotPerSec) >= 1.1 && s.getLengthInSeconds() <= 40.0)
                    && !((((double) s.getAveMotionPerSecond() / (double) aveMotion) >= 1.05) && s.getLengthInSeconds() <= 40.0)
               ) {
                // looks bad enough to delete
                scenes.get(i).setAd(false);
            } else {
                break;
            }
        }

        /* GO BACKWARDS NOW */
        searchStop = scenes.get(startScene).getSceneAudioEndPositionBytes()/4000;
        searchStart  = scenes.get(endScene).getSceneAudioEndPositionBytes()/4000;
        lowIntensityAudioBytePosition = 0;
        foundLowIntensityFrame = false;

        forLoop:
        for (int j = (int) searchStart; j >= searchStop; j--) {
            //        if (isZeroAudioIntensityFrame.get(j)) {

            if (j < aveIntensities.size()) {
                if ((aveIntensities.get(j) <= 0) || ((double) aveIntensities.get(j)/vidAveAudioIntensity >= 1.5) 
                        || ((double) aveIntensities.get(j)/vidAveAudioIntensity <= 0.5)) {
                    foundLowIntensityFrame = true;

                    // calculate end scene location
                    lowIntensityAudioBytePosition = 4000*j;
                    break forLoop;
                }
            }
        }

        maxInclusiveScene = 0;
        if (foundLowIntensityFrame) {
            for (int j = endScene; j >= startScene; j--) {
                if (scenes.get(j).getSceneAudioEndPositionBytes() > lowIntensityAudioBytePosition) {
                    maxInclusiveScene = j;
                    break;
                }
            }
        }

        // go front to back and delete everything before first with motion or low intensity
        for (int i = endScene; i >= startScene; i--) {
            Scene s = scenes.get(i);

            // search scene for low intensity
            if (i == maxInclusiveScene && this.numOfLowIntensityFrames < 300) {
                break;
            }

            // make sure it doesn't comply with motion and scenePerSec thresholds
            if ( s.getScorePoints() < 3 &&
                    !((s.getShotPerSec()/aveShotPerSec) >= 1.1 && s.getLengthInSeconds() <= 40.0)
                    && !((((double) s.getAveMotionPerSecond() / (double) aveMotion) >= 1.05) && s.getLengthInSeconds() <= 40.0)
               ) {
                // looks bad enough to delete
                scenes.get(i).setAd(false);
            } else {
                break;
            }
        }


    }
}
