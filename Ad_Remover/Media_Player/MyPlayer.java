/*
 * CSCI 576 - Final Project 
 * April 5, 2013
 */

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;
import javax.swing.event.*;
import java.io.*;
import javax.swing.*;
import java.text.*;
import java.util.*;


public class MyPlayer implements ActionListener, ChangeListener {
    // defaults
    private String videoFileName = "";
    private String audioFileName = "";
    private int width = 352;
    private int height = 288;

    // buffers, input streams, etc.
    private byte[] bytesR;
    private byte[] bytesG;
    private byte[] bytesB;
    private byte[] bytesRGBBufferA;
    private byte[] bytesRGBBufferB;
    private static final int RBG_BUFFER_SIZE_IN_FRAMES = 100;
    private int rgbBufferFramePosition = 0;
    private int numRvalsInFrame;
    private int numGvalsInFrame;
    private int numBvalsInFrame;
    private BufferedImage currFrame;
    private InputStream inV;
//    private BufferedInputStream inV;
    private InputStream inA;
    private static PlaySound playSound;
    private long totalNumFrames = 0;

    // frame/image display  
    private JFrame frame;
    private JLabel label;

    // rewind slider
    private JSlider playPosition;
    private long tickStep;
    private JButton playButton;
    private JPanel controlsPanel;
    private JLabel timeLabel;
    private Date timeDateHolder;
    private DateFormat timeFormat;
    private int currentSliderPos = 0;
  
    // button/slider status variables
    private Integer threadLock = new Integer(0);
    private Integer threadLockB = new Integer(0);
    private String playerState = "Playing";
   
    private int[][] inImageFrameBuffer;
    private int[][] outImageFrameBuffer;
    private BufferedImage outImage;

    public static void main(String[] args) {
        String videoFileName = "";
        String audioFileName = "";
        int width = 0;
        int height = 0;
        final MyPlayer mPlayer;

        // check all input parameters...
        if (args.length != 2 && args.length != 4) {
            throw new IllegalArgumentException("Invalid Number of arguments. Usage: java MyPlayer InputVideo InputAudio [Width Height]\n");
        }

        videoFileName = args[0];
        audioFileName = args[1];

        if(args.length == 4) {
            try {
                width = Integer.parseInt(args[2]);
                height = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unable to parse one of the command line arguments.\n" + e);
            }
        }

        if(width < 0 || height < 0) {
            throw new IllegalArgumentException("Ivalid output video dimensions.\n");
        } 

        if (width > 0 && height > 0) { // 
            mPlayer = new MyPlayer(videoFileName, audioFileName, width, height);
        } else {
            mPlayer = new MyPlayer(videoFileName, audioFileName);
        }

        System.out.println("Video: " + videoFileName);
        System.out.println("Audio: " + audioFileName);

        Runnable runnable = new Runnable() {
            public void run() {
                double interval = 1000/24; // there are 24 frames per second
                double time = 0.0d;
                int i = 0;
                int sliderPos = 0;
                boolean frameReadSuccessful = false;

                while(true) {

                    time = System.currentTimeMillis();

                    if(mPlayer.getPlayerState().equals("Stopped") || mPlayer.getPlayerState().equals("Updating")) { continue; }

                    synchronized(mPlayer.threadLock) {
                        //frameReadSuccessful = mPlayer.readInNextFrame();
                        frameReadSuccessful = mPlayer.readInNextFrameBuffered();
                        mPlayer.drawFrame();
                        if (!frameReadSuccessful) { continue; }
                        i++;

                        try {
                            playSound.playNextChunk();
                        } catch (PlayWaveException e) {
                            e.printStackTrace();
                            return;
                        }
                    }

                    if ((mPlayer.tickStep > 0) && (i % mPlayer.tickStep == 0)) {
                        mPlayer.updateSliderPosition(mPlayer.playPosition.getValue() + 1);
                    }

                    if (i % 24 == 0) {
                        mPlayer.timeDateHolder.setTime(mPlayer.timeDateHolder.getTime() + 1000);
                        mPlayer.timeLabel.setText(mPlayer.timeFormat.format(mPlayer.timeDateHolder));
                    }

                    // show only 1 frame per every 1000/24 miliseconds
                    time = interval - (System.currentTimeMillis() - time);

                    if (time > 0) {
                        try {
                            Thread.sleep((int)Math.round(time));
                        } catch (Exception ex) {
                            System.err.println("Error: " + ex);
                            System.exit(1);
                        }
                    }
                }
            }
        };
        Thread thread2 = new Thread(runnable);

        // start thread
        thread2.start();

    }

    public MyPlayer(String vFileName, String aFileName) {
        this.videoFileName = vFileName;
        this.audioFileName = aFileName;

        initPlayer();
    }

    public MyPlayer(String vFileName, String aFileName, int width, int height) {
        this.videoFileName = vFileName;
        this.audioFileName = aFileName;
        this.width = width;
        this.height = height;

        initPlayer();
    }

    private void initPlayer() {
        // init remaining instance vars 
        long videoFileSizeInBytes = 0;
        int totalPixelsInFrame = width * height;
        numRvalsInFrame = totalPixelsInFrame;
        numGvalsInFrame = totalPixelsInFrame;
        numBvalsInFrame = totalPixelsInFrame;
        bytesR = new byte[totalPixelsInFrame];
        bytesG = new byte[totalPixelsInFrame];
        bytesB = new byte[totalPixelsInFrame];
        // RGB frame buffer
        bytesRGBBufferA = new byte[totalPixelsInFrame * 3 * RBG_BUFFER_SIZE_IN_FRAMES];
        currFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

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

        // setup step size for our slider (there 100 positions, so find out 
        // how many frames need to be shown before we move up the slider to next
        // position)
        long bytesPerFrame = numRvalsInFrame + numGvalsInFrame + numBvalsInFrame;
        tickStep = videoFileSizeInBytes / bytesPerFrame / 1000;
        totalNumFrames = (long) Math.floor((videoFileSizeInBytes / bytesPerFrame));

        // create player display and populate it with blank (white) frame
        BufferedImage img = buildBlankImage();
        initDrawFrame(img); 
    }

    private boolean readInNextFrame() {
        synchronized(threadLock) {
        // read R,G,B bytes for next frame from video input stream
        try {
            // the order is B,G,R not R,G,B as was mentioned in Assignment Readme...
            int bBytesRead = inV.read(bytesB);
            int gBytesRead = inV.read(bytesG);
            int rBytesRead = inV.read(bytesR);

            if (rBytesRead != bytesR.length || gBytesRead != bytesG.length || bBytesRead != bytesB.length) {
                return false;
            }

            int ind = 0;
            for(int y = 0; y < height; y++){
                for(int x = 0; x < width; x++){

                    byte a = 0;
                    byte r = bytesR[ind];
                    byte g = bytesG[ind];
                    byte b = bytesB[ind]; 

                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                    currFrame.setRGB(x,y,pix);
                    ind++;
                }
            }

        } catch (IOException e) {
            System.err.println("Error reading video input file: " + e);
            e.printStackTrace();
            System.exit(1);
        }

        return true;
        }
    }

    private boolean readInNextFrameBuffered() {
        synchronized(threadLock) {
        // read R,G,B bytes for next frame from video input stream
        try {
            // the order is B,G,R not R,G,B as was mentioned in Assignment Readme...
            int rgbBytesRead = 0;

            if (rgbBufferFramePosition == 0 || rgbBufferFramePosition == (RBG_BUFFER_SIZE_IN_FRAMES + 1)) { // read from disk every 100 frames
                synchronized(threadLockB) {
                    if (bytesRGBBufferB != null) { // already have data in backup buffer
                        bytesRGBBufferA = bytesRGBBufferB;
                        rgbBytesRead = bytesRGBBufferB.length;
                        bytesRGBBufferB = null;
                    } else {
                        rgbBytesRead = inV.read(bytesRGBBufferA);
                    }
                }
                // replenish buffer B in the background
                Runnable runnable = new Runnable() {
                    public void run() {
                        synchronized(threadLockB) {
                            try {
                                bytesRGBBufferB = new byte[width*height * 3 * RBG_BUFFER_SIZE_IN_FRAMES];
                                int rgbBytesRead = 0;
                                rgbBytesRead = inV.read(bytesRGBBufferB);

                                if (rgbBytesRead != bytesRGBBufferB.length) { // buffer was not utilized 100%, this might be end of video
                                    bytesRGBBufferB = null;
                                }
                            } catch (IOException e) {
                                System.err.println("Error reading video input file: " + e);
                                e.printStackTrace();
                                System.exit(1);
                            }
                        }
                    }
                };

                Thread t = new Thread(runnable);
                t.start();


                if (rgbBytesRead != bytesRGBBufferA.length) { // buffer was not utilized 100%, this might be end of video
                    if (rgbBytesRead <= 0 || (rgbBytesRead % (numRvalsInFrame + numGvalsInFrame + numBvalsInFrame) != 0)) {
                        return false;
                    } else { // still valid, adjust buffer position accordingly
                        rgbBufferFramePosition = RBG_BUFFER_SIZE_IN_FRAMES - (int)(rgbBytesRead / (numRvalsInFrame + numGvalsInFrame + numBvalsInFrame));
                    }
                } else {
                    rgbBufferFramePosition = 1;
                }
            }

            int bOffset = ((rgbBufferFramePosition - 1) * (numRvalsInFrame + numGvalsInFrame + numBvalsInFrame));
            int gOffset = ((rgbBufferFramePosition - 1) * (numRvalsInFrame + numGvalsInFrame + numBvalsInFrame)) + numBvalsInFrame;
            int rOffset = ((rgbBufferFramePosition - 1) * (numRvalsInFrame + numGvalsInFrame + numBvalsInFrame)) + numBvalsInFrame + numGvalsInFrame;

            int ind = 0;
            for(int y = 0; y < height; y++){
                for(int x = 0; x < width; x++){

                    byte a = 0;
                    byte b = bytesRGBBufferA[ind + bOffset]; 
                    byte g = bytesRGBBufferA[ind + gOffset];
                    byte r = bytesRGBBufferA[ind + rOffset];

                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                    currFrame.setRGB(x,y,pix);
                    ind++;
                }
            }

            rgbBufferFramePosition++;

        } catch (IOException e) {
            System.err.println("Error reading video input file: " + e);
            e.printStackTrace();
            System.exit(1);
        }

        return true;
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
                    readInNextFrame();
                    drawFrame();
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

    private void initDrawFrame() {
        // Use a label to display the image
        this.frame = new JFrame();
        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.label = new JLabel(new ImageIcon(currFrame));
        label.setBorder(BorderFactory.createLineBorder(Color.BLACK,2));
        frame.getContentPane().add(label, BorderLayout.WEST);
        frame.pack();
        frame.setVisible(true);
    }

    private void drawFrame() {
        // Use a label to display the image
        this.label.setIcon(new ImageIcon(currFrame));
        frame.repaint();
    }

    private void addPlayerControls() {
        controlsPanel = new JPanel();
        controlsPanel.setLayout(new GridLayout(3, 1));
        //Create the slider.
        playButton = new JButton("Play/Pause");
        playButton.addActionListener(this);
        controlsPanel.add(playButton);
        playPosition = new JSlider(JSlider.HORIZONTAL, 0, 1000, 0);
        //playPosition.addChangeListener(this.frame);
 
        playPosition.setBorder(
                BorderFactory.createEmptyBorder(10,0,10,0));
        Font font = new Font("Serif", Font.ITALIC, 15);
        playPosition.setFont(font);
        playPosition.addChangeListener(this);
        controlsPanel.add(playPosition);
        timeLabel = new JLabel();
        timeFormat = new SimpleDateFormat("mm:ss");
        timeDateHolder = new Date(0);
        String time = timeFormat.format(timeDateHolder);  
        timeLabel.setText(time);  
        timeLabel.setBorder(BorderFactory.createEmptyBorder(0,10,3,10));
        controlsPanel.add(timeLabel);
        this.frame.getContentPane().add(controlsPanel,  BorderLayout.SOUTH);
    }

    private synchronized void updateSliderPosition(int pos) {
        if(pos >= 0 && pos <= 1000) {
            this.playPosition.setValue(pos);
        }
    }

    private BufferedImage buildBlankImage() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){

                byte a = 0;
                byte r = (byte) 255; //bytes[ind];
                byte g = (byte) 255; //bytes[ind+height*width];
                byte b = (byte) 255; //bytes[ind+height*width*2]; 

                int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                img.setRGB(x,y,pix);
            }
        }

        return img;
    }

    private void initDrawFrame(BufferedImage frameImg) {
        // Use a label to display the image
        this.frame = new JFrame();
        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.frame.setResizable(false);
        this.label = new JLabel(new ImageIcon(frameImg));
        label.setBorder(BorderFactory.createLineBorder(Color.BLACK,2));
        frame.getContentPane().add(label, BorderLayout.WEST);
        addPlayerControls();
        frame.pack();
        frame.setVisible(true);
    }

    private void drawFrame(BufferedImage frameImg) {
        // Use a label to display the image
        this.label.setIcon(new ImageIcon(frameImg));
        frame.repaint();
    }
 
    private synchronized void togglePlayerState() {
        if(playerState.equals("Playing")) {
            playerState = "Stopped";
        } else {
            playerState = "Playing";
        }
    }

    private synchronized String getPlayerState() {
        return playerState;
    }

    /* Play/Pause button was clicked */
    public void actionPerformed(ActionEvent e) { 
        togglePlayerState();
    }

    /* slider was adjusted */
    public void stateChanged(ChangeEvent e) {
        JSlider source = (JSlider)e.getSource();
        if (!source.getValueIsAdjusting()) {
            int position = (int)source.getValue();
            if (position != (currentSliderPos + 1)) {
                while(!getPlayerState().equals("Stopped")) { togglePlayerState(); } 
                int max = (int)source.getMaximum();

                // update time
                long timeMilliSecs =  Math.round((double)totalNumFrames * ((double)position/(double)max)) / 24 * 1000;
                timeDateHolder.setTime(timeMilliSecs);
                timeLabel.setText(timeFormat.format(timeDateHolder));

                if(position == max) {
                    position -= 1;
                }
                long newPos = Math.round((double)totalNumFrames * ((double)position/(double)max)) * width * height * 3;
                if(newPos == 0) {
                    newPos = 1;
                }

/*
                System.err.println("Slider Position: " + position);
                System.err.println("Slider Position (bytes): " + newPos);
                System.err.println("Slider Position (bytes) MAX: " + max);
*/

                synchronized(threadLock) {
                    rgbBufferFramePosition = 0;
                    bytesRGBBufferB = null;
                }
                resetVideoStream(newPos);

                // now calc. audio offset pos.
                long newAudioPos = Math.round((double)totalNumFrames * ((double)position/(double)max)) * 4000;
                resetAudioStream(newAudioPos);
                togglePlayerState();

            }
            currentSliderPos = position;
        }
    }


}
