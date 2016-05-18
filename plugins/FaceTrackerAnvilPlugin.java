/*
Anvil Face tracker Plug-in
Copyright (C) 2012 Copenhagen University, Center for Language Technology (CST)

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ItemEvent;

import java.awt.image.BufferedImage;
import java.awt.image.SampleModel;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JRadioButton;
import javax.swing.JCheckBox;
import javax.swing.ButtonGroup;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.media.Time;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;


import de.anvilsoft.AnnotationManager;
import de.anvilsoft.Anvil;
import de.anvilsoft.AnvilChangeListener;
import de.anvilsoft.AnvilChangeEvent;
import de.anvilsoft.annot.AnnContainer;
import de.anvilsoft.annot.AnnElement;
import de.anvilsoft.annot.impl.AnnPrimaryTrackImpl;
import de.anvilsoft.annot.impl.AnnProperties;
import de.anvilsoft.annot.impl.AnnProperty;
import de.anvilsoft.annot.AnnSet;
import de.anvilsoft.annot.impl.AnnStringProperty;
import de.anvilsoft.annot.AnnTrack;
import de.anvilsoft.annot.AnnotationFile;
import de.anvilsoft.annot.impl.AnnotationFileImpl;
import de.anvilsoft.annot.AnnotationChangeListener;
import de.anvilsoft.annot.impl.PrimaryIntervalElement;
import de.anvilsoft.annot.impl.ScreenPoint;
import de.anvilsoft.annot.impl.TimestampedScreenPoint;

import de.anvilsoft.annot.impl.VisualTrack;
import de.anvilsoft.annot.attribute.AnnPointsAttribute;
import de.anvilsoft.annot.attribute.AnnTimestampedPointsAttribute;
import de.anvilsoft.annot.spec.GroupSpec;
import de.anvilsoft.annot.spec.TrackSpec;
import de.anvilsoft.annot.valuetype.ValuePointsType;
import de.anvilsoft.gui.ContainerChangeEvent;
import de.anvilsoft.gui.SelectionChangeEvent;
import de.anvilsoft.gui.SelectionChangeListener;


import de.anvilsoft.gui.video.VideoOverlayPanel;
import de.anvilsoft.gui.video.VideoMouseListener;
import de.anvilsoft.media.MediaTimeChangeEvent;
import de.anvilsoft.media.MediaTimeChangeListener;
import de.anvilsoft.media.Video;
import de.anvilsoft.MediaManager;
import de.anvilsoft.plugin.Plugin; // This was deleted by Michael Kipp
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.*;
import org.opencv.objdetect.CascadeClassifier;

public class FaceTrackerAnvilPlugin extends JFrame
implements ActionListener, Plugin, MediaTimeChangeListener, ChangeListener,
SelectionChangeListener,
AnvilChangeListener, AnnotationChangeListener, MouseListener
    {
    static{ System.loadLibrary(Core.NATIVE_LIBRARY_NAME);}
    
    class Threshold
        {
        public int slideVal;
        public double inverseSquare;
        public int ellipseRadius;
        public void set(JSlider slider)
			{
            slideVal = slider.getValue() * 5;
            inverseSquare = 1.0/((double)(slideVal * slideVal));
            ellipseRadius = slideVal;
			buttSave.setEnabled(true);
			buttCancel.setEnabled(true);            
			buttHaar.setEnabled(true);            
			}
        };
    Threshold hthres = new Threshold();
    Threshold vthres = new Threshold();
    class Sagitta
        {
        double sagitta = 0;
        double newsagitta = 0;
        double sagittaH = 0.0; // horizontal component of current vector
        double sagittaV = 0.0; // vertical component of current vector
        double sagittaHoffset = 0.0; // horizontal offset
        double sagittaVoffset = 0.0; // vertical offset
        double scale = 1.0;
        Time onset = null;
        Time end = null; // onset must be > end because annotations don't overlap in Anvil
        Time busiest = null;
        public void reset()
            {
            sagitta = 0;
            newsagitta = 0;
            sagittaH = 0.0; // horizontal component of current vector
            sagittaV = 0.0; // vertical component of current vector
            sagittaHoffset = 0.0; // horizontal offset
            sagittaVoffset = 0.0; // vertical offset
            onset = null;
            end = null;
            busiest = null;
            }
        Sagitta(double Scale)
            {
            scale = Scale;
            reset();
            }
        public void ArrowStuff(double hVal,double vVal,int ind,String attribute,Color belowThreshold,Color aboveThreshold)
            {
            /*
            hVal and vVal are pixels/second {velocity} or pixels/(second*second) {acceleration}  or pixels/(second*second*second) {jerk}
            (Future: jounce = pixels/(second*second*second*second) ?)
            */
            double h_component = hVal * scale;
            double v_component = vVal * scale;
            /*
            scale is 5 {velocity} or 1 {acceleration}
            scale allows us to use same sliders, without the visually distressing change of tickmarks.
            */
            /*
            * Make an arrow to be displayed in
            * the overlay panel.
            */
            java.awt.Point q1 = new java.awt.Point();
            java.awt.Point q2 = new java.awt.Point();
            java.awt.Point pathQ[] = {q1, q2};
            q1.x = H;
            q1.y = V;
            q2.x = (int) (H + vectorSizePerUnitOfHeadSize * h_component);
            q2.y = (int) (V + vectorSizePerUnitOfHeadSize * v_component);

            /*
            * Compute averaged head speed or acceleration.
            * (squared)
            */
            newsagitta = (hthres.inverseSquare * h_component * h_component + vthres.inverseSquare * v_component * v_component);
            boolean thresholdreached = false;
            if(   doAllFrames
              ||  doMinima && newsagitta < squareOfHeadSizePerUnitOfVectorSize
              || !doMinima && newsagitta > squareOfHeadSizePerUnitOfVectorSize
              )
                {
                thresholdreached = true;
                typa.setLineColor(aboveThreshold);
                if(newsagitta > sagitta)
                    {
                    sagitta = newsagitta;
                    sagittaHoffset = H;
                    sagittaVoffset = V;
                    sagittaH = h_component * vectorSizePerUnitOfHeadSize;
                    sagittaV = v_component * vectorSizePerUnitOfHeadSize;
                    busiest = Ts[(index - period) % seqsiz];
                    }
                if(onset == null)
                    {
                    onset = Ts[(index - period) % seqsiz];
                    if(end != null && onset.getNanoseconds() < end.getNanoseconds())
                        onset = end;
                    }
                end = Ts[ind];
                }
            if(doAllFrames)
                {
                int middle = index - (period+1)/2;
                onset = Ts[middle % seqsiz];
                end = Ts[(middle+1) % seqsiz];
                busiest = onset;
                thresholdreached = false;
                }
            if(!thresholdreached)
                {
                typa.setLineColor(belowThreshold);
                if(onset != null)
                    /*
                    * Average vector length is decreasing.
                    * Assume that head movement has
                    * stopped.
                    */
                    {
                    try 
                        {
                        if (movementIntervalsTrack == null) 
                            {
                            init();
                            }
                        PrimaryIntervalElement newel = new PrimaryIntervalElement(movementIntervalsTrack, onset, end);
           
                        typabiggest.setLineColor(aboveThreshold);

                        AnnTimestampedPointsAttribute pt2 = new AnnTimestampedPointsAttribute(attribute, typabiggest);
                        newel.setAttribute(pt2);
                        movementIntervalsTrack.addElement(newel);
                        /*
                        * Add vector to
                        * the new interval element
                        */
                        //     High vertical value = low in picture |
                        //                                          v
                        double theta = java.lang.Math.atan2((double)-sagittaV,(double)sagittaH) * 180.0 / java.lang.Math.PI;
                        double rho = java.lang.Math.sqrt((double)(sagittaV*sagittaV)+(double)(sagittaH*sagittaH));
                        theta = -theta;
                        if(theta < 0.0)
                            theta += 360;
                        theta += 105; // half past eleven
                        int direction = (int)theta;
                        direction %= 360;
                        direction /= 30; // 0 .. 11
                        if(direction == 0)
                            direction = 12;
                        pt2.addPoint(new TimestampedScreenPoint(busiest, (int)(rho), direction));
                        pt2.addPoint(new TimestampedScreenPoint(busiest, (int)sagittaHoffset, (int)sagittaVoffset));
                        pt2.addPoint(new TimestampedScreenPoint(busiest, (int)(sagittaHoffset + sagittaH), (int)(sagittaVoffset + sagittaV)));
                        }
                    catch (de.anvilsoft.annot.CantAddException ee) 
                        {
                        System.out.println("CantAddException");
                        }
                    catch (java.lang.NullPointerException EN) 
                        {
                        System.out.println("NullPointerException");
                        if (movementIntervalsTrack == null) 
                            {
                            return;
                            }
                        }
                    sagitta = 0.0;
                    onset = null;
                    busiest = null;
                    }
                }

            overlayPanel.setPath("arrow", typa, pathQ);
            }
        };
    Sagitta velocity = new Sagitta(5.0);
    Sagitta acceleration = new Sagitta(1.0);
    Sagitta jerk = new Sagitta(1.0);
    
    private final String ANN_TEXT = "annotation: ";
    private final String ACTIVE_TRACK_TEXT = "track: ";
    private final String EMPTY_TEXT = "---";

    private final int WIDTH = 320;
    private final int HEIGHT = 510;

    private Anvil main;

    private JLabel activeTrackLabelVar;
    private JLabel activeTrackLabelSelected;
    private JLabel HaarcascadeSelected;
    private JLabel messageLabel;

    private JSlider slide;
    private JSlider slideh;
    private JSlider slidev;
    private JButton buttDismiss = new JButton("Dismiss");
    private JButton buttSave = new JButton("Keep changes");
    private JButton buttCancel = new JButton("Cancel changes");
    private JButton buttHaar = new JButton("Haarcascade");
    private JCheckBox MinimaCheckBox;
    private JCheckBox LeftCheckBox;
    private JCheckBox RightCheckBox;
    private int slidehValueVelocity = 10;
    private int slidevValueVelocity = 10;
    private int slidehValueMax = 20;
    private int slidevValueMax = 20;
    private VisualTrack headtrack;
    private AnnPrimaryTrackImpl wordtrack = null;
    private AnnPrimaryTrackImpl movementIntervalsTrack = null;
    private Time oldtime;
    private ValuePointsType typ;
    private ValuePointsType typa; // arrow
    private ValuePointsType typabiggest; // arrow when biggest, to be saved in element
    private JFrame videoWindow = null;
    private Video masterVideoWindow = null;
    private VideoOverlayPanel overlayPanel = null;
    private VideoMouseListener videoMouseListener = null;
    private int overlayPanelHorSize = 0;
    private int overlayPanelVerSize = 0;
    private int horOffsetOverlayPanel = 0;
    private int verOffsetOverlayPanel = 0;
    private AnnSet pointSet = null;
    private AnnElement pointselement = null;
    private int xmouse = -1;
    private int ymouse = -1;
    private int horminClip = 10000; // left
    private int hormaxClip = 0;     // right
    private int vermaxClip = 0;     // bottom!
    private int verminClip = 10000; // top!
    private int prevNumberOfFaces = 0;
    private AnnotationFileImpl ann = null;
    private double t0 = 0.0;
    private int seqsiz = 25;
    private int period = 10; // must be <= seqsiz
    private int defaultperiodV = 7;  // must be <= seqsiz
    private int defaultperiodA = 14; // must be <= seqsiz
    private int defaultperiodJ = 21; // must be <= seqsiz
    private int index = 0; // incremented for every analysed frame 
    private int prevframenr = -1;
    private int framenr = -1;
    private boolean faceSeen = false;
    private boolean controlsTouched = false;
    /* 
     * Round robin lists: Ts ts vs hs 
     */
    private Time Ts[]; /* list for keeping candidate end-times for movement
                        * intervals. (The start of the movement interval is
                        * the variable 'onset')
                        */ 
    private double ts[]; // relative time counted from first recognized face
    private int vs[];    // vertical position of face center
    private int hs[];    // horizontal position of face center
    private MediaManager mediaDelegate = null;
    private boolean stepping = false;
    private boolean doVelocity = true;
    private boolean doAcceleration = false;
    private boolean doJerk = false;
	private boolean doMinima = false;
	private boolean doLHS = false;
	private boolean doRHS = false;
    private boolean doAllFrames = false;

    double headsize = 160.0; // 80 + 80, horizontal and vertical size of head's clip rectangle in a movie I've seen.
    double vectorSizePerUnitOfHeadSize = 1.0;
    double squareOfHeadSizePerUnitOfVectorSize = 1.0;
    private java.awt.Point path0[] = {};
    private boolean disableFaceTracking = true;

    private int xell = 0;
    private int yell = 0;
    private int well = 0;
    private int hell = 0;
    private AnnotationManager annotationManger = null;
    private String ActiveTrack = null;
    private Graphics g;
    private int H;
    private int V;
    private JButton buttStep;
    CascadeClassifier classifier;
    private JCheckBox noneButton;
    private JCheckBox allFramesCheckBox;
    private String HaarcascadesDir;
    private String defaultHaarcascade = "haarcascade_frontalface_alt.xml";
    private String selectedHaarcascade = defaultHaarcascade;
    
    public FaceTrackerAnvilPlugin()
        {
        String classifierName;
        HaarcascadesDir = "." + File.separator + "required" + File.separator + "extern" + File.separator + "OpenCV" + File.separator + "haarcascades" + File.separator;
        classifierName = HaarcascadesDir + selectedHaarcascade;
        classifier = new CascadeClassifier(classifierName);
        if ( classifier.empty() )
            {
            System.err.println("Error loading classifier file \"" + classifierName + "\".");
            System.exit(1);
            }
        oldtime = null;
        typ = new ValuePointsType();
        typ.setInterpolation(typ.LINEAR_INTERPOLATION);
        typ.setConnection(typ.LINE_CONNECTION);
        typ.setLineColor(Color.black);
        typ.setLineSize(1);
        typ.setLineTransparency(0.0f);
        typ.setMarkerColor(Color.red);
        typ.setMarkerFilled(true);
        typ.setMarkerNumbering(false);
        // dots in the corners of the clip area: red filled circles
        typ.setMarkerSize(0);
        typ.setTimestamped(false);

        typa = new ValuePointsType();
        typa.setInterpolation(typ.LINEAR_INTERPOLATION);
        typa.setConnection(typ.ARROW_CONNECTION);
        typa.setLineColor(Color.blue);
        typa.setLineSize(3);
        typa.setLineTransparency(0.0f);
        typa.setMarkerColor(Color.red);
        typa.setMarkerFilled(true);
        typa.setMarkerNumbering(false);
        // dots in the corners of the clip area: red filled circles
        typa.setMarkerSize(0);
        typa.setTimestamped(false);

        typabiggest = new ValuePointsType();
        typabiggest.setInterpolation(typ.LINEAR_INTERPOLATION);
        typabiggest.setConnection(typ.ARROW_CONNECTION);
        typabiggest.setLineColor(Color.yellow);
        typabiggest.setLineSize(3);
        typabiggest.setLineTransparency(0.0f);
        typabiggest.setMarkerColor(Color.red);
        typabiggest.setMarkerFilled(true);
        typabiggest.setMarkerNumbering(false);
        // dots in the corners of the clip area: red filled circles
        typabiggest.setMarkerSize(0);
        typabiggest.setTimestamped(false);



        setLocationRelativeTo(null); // Centers JFrame on the screen
        Ts = new Time[seqsiz];
        ts = new double[seqsiz];
        vs = new int[seqsiz];
        hs = new int[seqsiz];
        }

    private void exit()
        {
        dispose();
        mediaDelegate.removeMediaTimeChangeListener(this);
        main.removeSubwindow(this);
        main.pluginExited(this);
        }

    public boolean canHaveMultipleInstances()  // de.anvilsoft.plugin.Plugin
        {
        return false;
        }

    public boolean exitFromAnvil()  // de.anvilsoft.plugin.Plugin
        {
        dispose();
        return true;
        }

    public boolean startFromAnvil(Anvil main) // de.anvilsoft.plugin.Plugin
        {
        this.main = main;
        main.addAnvilChangeListener(this);
        mediaDelegate = main.getMediaManager();
        mediaDelegate.addMediaTimeChangeListener(this);
        annotationManger = main.getAnnotationManger();

        addWindowListener   (
                            new WindowAdapter()
                                {
                                public void windowClosing(WindowEvent e)
                                    {
                                    exit();
                                    }
                                }
                            );

        setTitle("Face Tracker");

        JPanel info = new JPanel();
        info.setLayout(new GridLayout(0, 1));

        activeTrackLabelVar = new JLabel(EMPTY_TEXT);
        activeTrackLabelVar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        activeTrackLabelSelected = new JLabel(EMPTY_TEXT);
        activeTrackLabelSelected.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		
        HaarcascadeSelected = new JLabel(selectedHaarcascade);
        HaarcascadeSelected.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		
		messageLabel = new JLabel("");
        messageLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        JPanel buttons = new JPanel();
        buttons.setLayout(new GridLayout(0, 2,12,12));
        buttStep = new JButton("Step");
        buttStep.setActionCommand("Step");

        buttDismiss.addActionListener(this);
        buttStep.addActionListener(this);
        buttSave.addActionListener(this);
        buttCancel.addActionListener(this);
        buttHaar.addActionListener(this);
        buttStep.setEnabled(false);
        buttSave.setEnabled(false);
		buttCancel.setEnabled(false);
		buttHaar.setEnabled(true);

        noneButton = new JCheckBox("Suspend face tracking");
        noneButton.setActionCommand("none");
        noneButton.setSelected(true);

        allFramesCheckBox = new JCheckBox("Annotate every frame");
        allFramesCheckBox.setActionCommand("allframes");
        allFramesCheckBox.setSelected(true);

		MinimaCheckBox = new JCheckBox(EMPTY_TEXT); 
        MinimaCheckBox.setActionCommand("Minima");
		LeftCheckBox = new JCheckBox(EMPTY_TEXT); 
        LeftCheckBox.setActionCommand("Left");
		RightCheckBox = new JCheckBox(EMPTY_TEXT); 
        RightCheckBox.setActionCommand("Right");

        noneButton.addActionListener(this);
        allFramesCheckBox.addActionListener(this);
		MinimaCheckBox.addActionListener(this);
		LeftCheckBox.addActionListener(this);
		RightCheckBox.addActionListener(this);

        buttons.add(buttStep);
        buttons.add(buttSave);
        buttons.add(buttDismiss);
        buttons.add(buttCancel);
        buttons.add(buttHaar);
        info.add(noneButton);
        info.add(allFramesCheckBox);
		info.add(MinimaCheckBox);
		info.add(LeftCheckBox);
		info.add(RightCheckBox);
        info.add(activeTrackLabelVar);
        info.add(activeTrackLabelSelected);
        info.add(HaarcascadeSelected);
        info.add(messageLabel);
        
        JPanel slidersh = new JPanel();
        slidersh.setLayout(new BorderLayout());
        JPanel slidersv = new JPanel();
        slide = new JSlider(JSlider.HORIZONTAL, 0, seqsiz, period);
        slide.setMajorTickSpacing(5);
        slide.setMinorTickSpacing(1);
        slide.setSnapToTicks(true);
        slide.setPaintTicks(true);
        slide.setPaintLabels(true);
        slide.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        slide.addChangeListener(this);

        slideh = new JSlider(JSlider.HORIZONTAL, 0, slidehValueMax, slidehValueVelocity);
        slideh.setMajorTickSpacing(slidehValueMax/10);
        slideh.setMinorTickSpacing(slidehValueMax/20);
        slideh.setSnapToTicks(true);
        slideh.setPaintTicks(true);
        slideh.setPaintLabels(true);
        slideh.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        slideh.addChangeListener(this);

        slidev = new JSlider(JSlider.VERTICAL, 0, slidevValueMax, slidevValueVelocity);
        slidev.setMajorTickSpacing(slidevValueMax/10);
        slidev.setMinorTickSpacing(slidevValueMax/20);
        slidev.setSnapToTicks(true);
        slidev.setPaintTicks(true);
        slidev.setPaintLabels(true);
        slidev.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        slidev.addChangeListener(this);

        JPanel slidepanel = new JPanel();
        slidepanel.setLayout(new BorderLayout());
        JLabel slideLabel = new JLabel("# of frames to analyse", JLabel.CENTER);
        slideLabel.setAlignmentX((float)0.7);
        slidepanel.add(BorderLayout.NORTH, slideLabel);
        slidepanel.add(BorderLayout.SOUTH, slide);

        JPanel slidehpanel = new JPanel();
        slidehpanel.setLayout(new BorderLayout());
        JLabel slidehLabel = new JLabel("horizontal threshold", JLabel.CENTER);
        slidehLabel.setAlignmentX((float)0.7);
        slidehpanel.add(BorderLayout.NORTH, slidehLabel);
        slidehpanel.add(BorderLayout.SOUTH, slideh);

        slidersh.add(BorderLayout.NORTH,slidepanel);
        slidersh.add(BorderLayout.SOUTH,slidehpanel);

        JPanel slidevpanel = new JPanel();
        slidevpanel.setLayout(new BorderLayout());
        JLabel slidevNLabel = new JLabel("vertical", JLabel.CENTER);
        JLabel slidevSLabel = new JLabel("threshold", JLabel.CENTER);
        slidevNLabel.setAlignmentX((float)0.1);
        slidevSLabel.setAlignmentX((float)0.9);
        slidevpanel.add(BorderLayout.NORTH, slidevNLabel);
        slidevpanel.add(BorderLayout.CENTER, slidevSLabel);
        slidevpanel.add(BorderLayout.SOUTH, slidev);

        slidersv.add(slidevpanel);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(BorderLayout.CENTER, info);
        getContentPane().add(BorderLayout.NORTH, slidersh);
        getContentPane().add(BorderLayout.WEST, slidersv);
        getContentPane().add(BorderLayout.SOUTH, buttons);

        pack();
        setSize(WIDTH, HEIGHT);
        setVisible(true);

        main.addSubwindow(this);
        ann = main.getAnnotationFile();
        if(ann != null)
            {
            ann.addAnnotationChangeListener(this);
            AnnotationManager w = main.getAnnotationManger();
            w.addSelectionChangeListener(this);
            init();
            }

        masterVideoWindow = mediaDelegate.getMasterVideo();
		
		hthres.set(slideh);
		vthres.set(slidev);
		setSettingsEnabled(false);
		buttSave.setEnabled(false);
		buttCancel.setEnabled(false);            
		buttHaar.setEnabled(true);            
        return true;
        }


    /**
    * Listens to slider changes.
    */

    public void stateChanged(ChangeEvent e) // javax.swing.event.ChangeListener
        {
        if (!slide.getValueIsAdjusting()) 
            {
            period = slide.getValue();
            if(doJerk)
                {
                if(period < 4)
                    {
                    slide.setValue(4);
                    period = 4;
                    }
                }
            else if(doAcceleration)
                {
                if(period < 3)
                    {
                    slide.setValue(3);
                    period = 3;
                    }
                }
            else
                {
                if(period < 2)
                    {
                    slide.setValue(2);
                    period = 2;
                    }
                }
            buttSave.setEnabled(true);
			buttCancel.setEnabled(true);
			buttHaar.setEnabled(true);
			controlsTouched = true;
            }
        if (!slideh.getValueIsAdjusting()) 
            {
			hthres.set(slideh);
			controlsTouched = true;
            }
        if (!slidev.getValueIsAdjusting()) 
            {
			vthres.set(slidev);
			controlsTouched = true;
            }
        xell = H - hthres.ellipseRadius;
        yell = V - vthres.ellipseRadius;
        well = 2 * hthres.ellipseRadius;
        hell = 2 * vthres.ellipseRadius;
        try {
            g.clearRect(0, 0, well, hell);
            g.drawArc(0, 0, well, hell, 0/*int startAngle*/, 360/*int arcAngle*/);
            overlayPanel.update();                
            }
        catch(java.lang.NullPointerException err)
            {
            }
        }

    public void setSettingsEnabled(boolean val)
		{
        slide.setEnabled(val);
        slideh.setEnabled(val);
        slidev.setEnabled(val);
        MinimaCheckBox.setEnabled(val);
        LeftCheckBox.setEnabled(val);
        RightCheckBox.setEnabled(val);
        allFramesCheckBox.setEnabled(val);
		}

    public void setBegin()
		{
        slideh.setPaintTicks(false);
        slidev.setPaintTicks(false);
        slideh.setPaintLabels(false);
        slidev.setPaintLabels(false);
		}

    public void setEnd()
		{
        hthres.set(slideh);
        vthres.set(slidev);
        slideh.setPaintTicks(true);
        slidev.setPaintTicks(true);
        slideh.setPaintLabels(true);
        slidev.setPaintLabels(true);
        startBuffering();
        if(disableFaceTracking)
			{
			buttStep.setText(doStepping(stepping));
			buttStep.setEnabled(true);
			disableFaceTracking = false;
			}
		setSettingsEnabled(true);
		}

    public void setVelocity()
		{
		setBegin();
        doVelocity = true;
        MinimaCheckBox.setText("Annotate when velocity is LOW.");
        LeftCheckBox.setText("Left");
        RightCheckBox.setText("Right");
        allFramesCheckBox.setText("Annotate All Frames");
        if(period < 2)
            {
            slide.setValue(2);
            period = 2;
            }
        doAcceleration = false;
        doJerk = false;
		noneButton.setSelected(false);
        setEnd();
		}

    public void setAcceleration()
		{
		setBegin();
        doVelocity = false;
        doAcceleration = true;
        doJerk = false;
        MinimaCheckBox.setText("Annotate when acceleration is LOW.");
        LeftCheckBox.setText("Left");
        RightCheckBox.setText("Right");
        allFramesCheckBox.setText("Annotate All Frames");
        if(period < 3)
            {
            slide.setValue(3);
            period = 3;
            }
		noneButton.setSelected(false);
		setEnd();
		}
		
    public void setJerk()
		{
		setBegin();
        doVelocity = false;
        doAcceleration = false;
        doJerk = true;
        MinimaCheckBox.setText("Annotate when jerk is LOW.");
        LeftCheckBox.setText("Left");
        RightCheckBox.setText("Right");
        if(period < 3)
            {
            slide.setValue(3);
            period = 3;
            }
		noneButton.setSelected(false);
		setEnd();
		}
		
    public void setNone()
		{
        MinimaCheckBox.setText(EMPTY_TEXT);
        LeftCheckBox.setText(EMPTY_TEXT);
        RightCheckBox.setText(EMPTY_TEXT);
        allFramesCheckBox.setText(EMPTY_TEXT);
        doVelocity = false;
        doAcceleration = false;
        doJerk = false;
		noneButton.setSelected(true);
        disableFaceTracking = true;
        doStepping(false);
        buttStep.setText(EMPTY_TEXT);
        buttStep.setEnabled(false);
        setSettingsEnabled(false);
		}		
		
    public void setAllFrames()
		{
		allFramesCheckBox.setSelected(true);
		}		
		
	public void saveTrackSettings()
        {
        if(ann != null && ActiveTrack != null)
            {
            AnnTrack activeTrack = ann.getTrack(ActiveTrack);
            if (activeTrack != null) 
                {
                try 
                    {
                    java.util.List list = activeTrack.getAttributeNames();
                    if(  list.contains("acceleration")
				      || list.contains("velocity")
				      || list.contains("jerk")
				      )
						{
						AnnProperties annProperties = ann.getProperties();
						annProperties.addProperty("HorSensitivity."+ActiveTrack,new AnnStringProperty(Integer.toString(hthres.slideVal)));
						annProperties.addProperty("VerSensitivity."+ActiveTrack,new AnnStringProperty(Integer.toString(vthres.slideVal)));
						annProperties.addProperty("Frames."+ActiveTrack,new AnnStringProperty(Integer.toString(period)));
						annProperties.addProperty("doMinima."+ActiveTrack,new AnnStringProperty(doMinima ? "1" : "0"));
						annProperties.addProperty("LHS."+ActiveTrack,new AnnStringProperty(doLHS ? "1" : "0"));
						annProperties.addProperty("RHS."+ActiveTrack,new AnnStringProperty(doRHS ? "1" : "0"));
						annProperties.addProperty("AllFrames."+ActiveTrack,new AnnStringProperty(doAllFrames ? "1" : "0"));
						annProperties.addProperty("Haarcascade."+ActiveTrack,new AnnStringProperty(selectedHaarcascade));
						buttSave.setEnabled(false);
						buttCancel.setEnabled(false);
						buttHaar.setEnabled(true);
						controlsTouched = false;
						ann.setModified(true);            
						}
                    }
                catch(java.lang.ClassCastException ee)
                    {
                    messageLabel.setText("Error: cannot get attribute names.");
                    }
                }
            }
        }

	public void cancelTrackSettings()
        {
        if(ann != null && ActiveTrack != null)
            {
            AnnTrack activeTrack = ann.getTrack(ActiveTrack);
            if (activeTrack != null) 
                {
                try 
                    {
                    java.util.List list = activeTrack.getAttributeNames();
                    if(list.contains("velocity"))
						{
						setControls(defaultperiodV);
						buttSave.setEnabled(false);
						buttCancel.setEnabled(false);            
						buttHaar.setEnabled(true);            
						}
                    if(list.contains("acceleration"))
						{
						setControls(defaultperiodA);
						buttSave.setEnabled(false);
						buttCancel.setEnabled(false);            
						buttHaar.setEnabled(true);            
						}
                    if(list.contains("jerk"))
						{
						setControls(defaultperiodJ);
						buttSave.setEnabled(false);
						buttCancel.setEnabled(false);            
						buttHaar.setEnabled(true);            
						}
                    }
                catch(java.lang.ClassCastException ee)
                    {
                    messageLabel.setText("Error: cannot get attribute names.");
                    }
                }
            }
        }

    public boolean setControls(int defaultperiod)
		{            
        boolean notFoundInFile = false;
        AnnProperties annProperties = ann.getProperties();
        AnnProperty prop;
        int value;
        
        prop = annProperties.getProperty("HorSensitivity."+ActiveTrack);
        if(prop != null)
			{
			value = Integer.parseInt(prop.getContent())/5;
			}
		else
			{
			value = slidehValueVelocity;
			notFoundInFile = true;
			}
		slideh.setValue(value);
		hthres.set(slideh);
		
        prop = annProperties.getProperty("VerSensitivity."+ActiveTrack);
        if(prop != null)
			{
			value = Integer.parseInt(prop.getContent())/5;
			}
		else
			{
			value = slidevValueVelocity;
			notFoundInFile = true;
			}
		slidev.setValue(value);
		vthres.set(slidev);
		
        prop = annProperties.getProperty("Frames."+ActiveTrack);
        if(prop != null)
			{
			period = Integer.parseInt(prop.getContent());
			}
		else
			{
			period = defaultperiod;
			notFoundInFile = true;
			}
        slide.setValue(period);
			
        prop = annProperties.getProperty("AllFrames."+ActiveTrack);
        if(prop != null)
			{
			doAllFrames = Integer.parseInt(prop.getContent()) == 1;
			}
		else
			{
			doAllFrames = false;
			notFoundInFile = true;
			}
		allFramesCheckBox.setSelected(doAllFrames);
			
        prop = annProperties.getProperty("doMinima."+ActiveTrack);
        if(prop != null)
			{
			doMinima = Integer.parseInt(prop.getContent()) == 1;
			}
		else
			{
			doMinima = false;
			notFoundInFile = true;
			}
		MinimaCheckBox.setSelected(doMinima);
		
        prop = annProperties.getProperty("LHS."+ActiveTrack);
        if(prop != null)
			{
			doLHS = Integer.parseInt(prop.getContent()) == 1;
			}
		else
			{
			doLHS = false;
			notFoundInFile = true;
			}
		LeftCheckBox.setSelected(doLHS);

        prop = annProperties.getProperty("RHS."+ActiveTrack);
        if(prop != null)
			{
			doRHS = Integer.parseInt(prop.getContent()) == 1;
			}
		else
			{
			doRHS = false;
			notFoundInFile = true;
			}
		RightCheckBox.setSelected(doRHS);

        prop = annProperties.getProperty("Haarcascade."+ActiveTrack);
        if(prop != null)
			{
			selectedHaarcascade = prop.getContent();
			}
		else
			{
			selectedHaarcascade = defaultHaarcascade;
			notFoundInFile = true;
			}
	    HaarcascadeSelected.setText(selectedHaarcascade);
        classifier.load(HaarcascadesDir+selectedHaarcascade);
        if ( classifier.empty() )
            {
            System.err.println("Error loading classifier file \"" + HaarcascadesDir + selectedHaarcascade + "\".");
            System.exit(1);
            }

		controlsTouched = false;
		return notFoundInFile;
		}


	public boolean getTrackSettings()
        {
        boolean notfoundInFile = false;
        if(ann != null && ActiveTrack != null)
            {
            AnnTrack activeTrack = ann.getTrack(ActiveTrack);
            if (activeTrack != null) 
                {
                activeTrackLabelVar.setText(ACTIVE_TRACK_TEXT+activeTrack);
                try 
                    {
                    java.util.List list = activeTrack.getAttributeNames();
                    if(list.contains("acceleration"))
						{
			            notfoundInFile = setControls(defaultperiodA);
						setAcceleration();
						movementIntervalsTrack = (AnnPrimaryTrackImpl) activeTrack;
						activeTrackLabelSelected.setText("Acceleration track");
						messageLabel.setText("");
						}
					else if(list.contains("velocity"))
						{
			            notfoundInFile = setControls(defaultperiodV);
						setVelocity();
						movementIntervalsTrack = (AnnPrimaryTrackImpl) activeTrack;
						activeTrackLabelSelected.setText("Velocity track");
						messageLabel.setText("");
						}
					else if(list.contains("jerk"))
						{
			            notfoundInFile = setControls(defaultperiodJ);
						setJerk();
						movementIntervalsTrack = (AnnPrimaryTrackImpl) activeTrack;
						activeTrackLabelSelected.setText("Jerk track");
						messageLabel.setText("");
						}						
					else
						{
						activeTrackLabelSelected.setText(EMPTY_TEXT);
						messageLabel.setText("(Not a track for head movements.)");
						setNone();
						}
                    }
                catch(java.lang.ClassCastException ee)
                    {
					setNone();
					activeTrackLabelSelected.setText(EMPTY_TEXT);
					messageLabel.setText("(Not a track for head movements.)");
                    }
                }
            }
        return notfoundInFile;
        }


    public String doStepping(boolean stepping)
        {
        String res = "";
        if(stepping)
            {
            if(ann == null)
                res = "?";
            else if(mediaDelegate.isPlaying())
                res = "Normal speed";
            else
                res = "Stop";
                
            if(ann != null && main.getAnnoBoard() != null)
                framenr = main.getAnnoBoard().getPlayline() + 1;
            else
                framenr = -1;

            if(prevframenr + 2 == framenr)
                framenr = prevframenr;
            else
                startBuffering();

            if(ann != null)
                mediaDelegate.setVideoFrame(framenr);
            }
        else
            res = "Step";
        return res;
        }



    /**
    * Listens to the buttons.
    */

    public void actionPerformed(ActionEvent e) // Interface ActionListener
        {
        String com = e.getActionCommand();
        if (com.equals("Dismiss")) 
            {
            if(overlayPanel != null)
                {
                overlayPanel.clearShapes();
                overlayPanel.setPath("faces", typ, path0);
                }
            exit();
            }
        else if(com.equals("Step")) 
            {
            stepping = !stepping;
            buttStep.setText(doStepping(stepping));
            }
        else if(com.equals("Keep changes"))
            {
            saveTrackSettings();
            }
        else if(com.equals("Cancel changes"))
            {
            cancelTrackSettings();
            }
        else if(com.equals("none"))
            {
            JCheckBox cb = (JCheckBox)e.getSource();
            if(cb.isSelected())
				setNone();
			else
				{
				getTrackSettings();
				}
            }
        else if(com.equals("allframes"))
            {
            JCheckBox cb = (JCheckBox)e.getSource();
            doAllFrames = cb.isSelected();
			buttSave.setEnabled(true);
			buttCancel.setEnabled(true);            
			buttHaar.setEnabled(true);            
			controlsTouched = true;
            }
        else if(com.equals("Minima"))
            {
            JCheckBox cb = (JCheckBox)e.getSource();
            doMinima = cb.isSelected();
			buttSave.setEnabled(true);
			buttCancel.setEnabled(true);            
			buttHaar.setEnabled(true);            
			controlsTouched = true;
            }
        else if(com.equals("Left"))
            {
            JCheckBox cb = (JCheckBox)e.getSource();
            doLHS = cb.isSelected();
			buttSave.setEnabled(true);
			buttCancel.setEnabled(true);            
			buttHaar.setEnabled(true);            
			controlsTouched = true;
            }
        else if(com.equals("Right"))
            {
            JCheckBox cb = (JCheckBox)e.getSource();
            doRHS = cb.isSelected();
			buttSave.setEnabled(true);
			buttCancel.setEnabled(true);            
			buttHaar.setEnabled(true);            
			controlsTouched = true;
            }
        else if(com.equals("Haarcascade"))
            {
            JFileChooser fc = new JFileChooser(HaarcascadesDir);

            // Show open dialog; this method does not return until the dialog is closed
            int retval = fc.showOpenDialog(FaceTrackerAnvilPlugin.this);
            if(retval == JFileChooser.APPROVE_OPTION)
                {
                selectedHaarcascade = fc.getSelectedFile().getName();
                HaarcascadeSelected.setText(selectedHaarcascade);
                classifier.load(HaarcascadesDir+selectedHaarcascade);
                if ( classifier.empty() )
                    {
                    System.err.println("Error loading classifier file \"" + HaarcascadesDir + selectedHaarcascade + "\".");
                    System.exit(1);
                    }
                }
            }
        }

    /**
    * Called each time a new annotation is loaded to Anvil.
    */

    public void annotationCreated(AnvilChangeEvent e)
        // Interface AnvilChangeListener
        {
        /* Does this work as intended? ViewerChangeListener methods are 
        never called. (selectedElementChanged, activeContainerChanged) */
        annotationManger.addSelectionChangeListener(this);

        ann = e.getAnnotation();
        if (ann != null) 
            {
            ann.addAnnotationChangeListener(this);
            }
        }

    /*
    init is called if an anvil file is opened after the plugin has been loaded.
    */
    public void init()
        {
        setNone();
        if (ann != null) 
            {
            buttStep.setEnabled(false);
            buttSave.setEnabled(false);
            buttCancel.setEnabled(false);
			buttHaar.setEnabled(true);            
			controlsTouched = false;
            ann.addAnnotationChangeListener(this);
            if(ActiveTrack != null)
                {
                AnnTrack activeTrack = ann.getTrack(ActiveTrack);
                if (activeTrack != null) 
                    {
                    movementIntervalsTrack = (AnnPrimaryTrackImpl) activeTrack;
                    }
                else 
                    {
                    disableFaceTracking = true;
                    System.out.println("Not the right spec. Cannot use track '" + ActiveTrack + "'. Disabling FaceTracking!");
                    }
                }
            }
        }

    public void annotationLoaded(AnvilChangeEvent e)
        {
        AnnotationManager w = main.getAnnotationManger();
        w.addSelectionChangeListener(this);
        ann = e.getAnnotation();
        init();
        }

    public void annotationClosed(AnvilChangeEvent e)
        {
        wordtrack = null;
        movementIntervalsTrack = null;
        overlayPanel = null;
        videoWindow = null;
        pointSet = null;
        pointselement = null;
        }

    /**
    * Called each time a new track element is selected.
    */
    public void selectedElementChanged(SelectionChangeEvent e)
        {
        AnnElement el = e.getElement();
        if (el != null) 
            {
            }
        else 
            {
            }
        }

    public void selectedAnnotationFileChanged(AnnotationFileImpl annotationFile)
        {
        }

    /**
    * Called each time the track is changed.
    */
    public void activeContainerChanged(ContainerChangeEvent e)
        {
        AnnContainer container = e.getContainer();

        if (container != null) 
            {
            boolean cancelled = buttSave.isEnabled() && controlsTouched;
            //saveTrackSettings();
            ActiveTrack = container.longName();
            activeTrackLabelVar.setText(ACTIVE_TRACK_TEXT+ActiveTrack);
            boolean notfoundSettings = getTrackSettings();
			buttSave.setEnabled(notfoundSettings);
			buttCancel.setEnabled(notfoundSettings);
			buttHaar.setEnabled(true);            
            if(cancelled)
				messageLabel.setText("   (Changes of settings cancelled!)");
            }
        }

    public void bookmarkAction(AnnotationFile annotation, java.lang.String action)
        {
        }

    public void elementChanged(AnnotationFile annotation, AnnContainer container, AnnElement element, de.anvilsoft.action.AnvilAction action)
        {
        }

    public void clipCorrect()
        {
        if(doLHS && !doRHS)
			{
			if (horminClip < 0) 
				{
				horminClip = 0;
				}
			else if (horminClip > overlayPanelHorSize / 2) 
				{
				horminClip = overlayPanelHorSize / 2 - 1;
				}
			if (hormaxClip > overlayPanelHorSize / 2) 
				{
				hormaxClip = overlayPanelHorSize / 2;
				}
			}
		else if(doRHS && !doLHS)
			{
			if (horminClip < overlayPanelHorSize / 2) 
				{
				horminClip = overlayPanelHorSize / 2;
				}
			if (hormaxClip < overlayPanelHorSize / 2) 
				{
				hormaxClip = overlayPanelHorSize / 2 + 1;
				}
			else if (hormaxClip > overlayPanelHorSize) 
				{
				hormaxClip = overlayPanelHorSize;
				}
			}
		else
			{
			if (horminClip < 0) 
				{
				horminClip = 0;
				}
			if (hormaxClip > overlayPanelHorSize) 
				{
				hormaxClip = overlayPanelHorSize;
				}
            }
            
        if (verminClip < 0) 
            {
            verminClip = 0;
            }
        if (vermaxClip > overlayPanelVerSize) 
            {
            vermaxClip = overlayPanelVerSize;
            }
			
			
        }

    public void startBuffering()
        {
        horminClip = xmouse - 10;
        verminClip = ymouse - 10;
        hormaxClip = xmouse + 10;
        vermaxClip = ymouse + 10;
        prevNumberOfFaces = 0;

        acceleration.reset();
        velocity.reset();
        jerk.reset();
        setVectorSizePerUnitOfHeadSize();

        xell = 0;
        yell = 0;
        well = 0;
        hell = 0;

        t0 = 0.0;
        period = slide.getValue();
        index = 0;
        }

    public void mouseClicked(java.awt.event.MouseEvent e)
        {
        //Invoked when the mouse button has been clicked (pressed and released) on a component.
        xmouse = e.getX();
        ymouse = e.getY();
        startBuffering();
        faceSeen = false;
        }

    public void mousePressed(java.awt.event.MouseEvent e)
        {
        //Invoked when a mouse button has been pressed on a component.
        }

    public void mouseReleased(java.awt.event.MouseEvent e)
        {
        //Invoked when a mouse button has been released on a component.
        }

    public void mouseEntered(java.awt.event.MouseEvent e)
        {
        //Invoked when the mouse enters a component.
        }

    public void mouseExited(java.awt.event.MouseEvent e)
        {
        //Invoked when the mouse exits a component. 
        }

    private double velocity(double St, double St2, double Sh, double Sth, double period)
        {
/*
first degree: a:position, b:velocity
var a
solution (a.period^-1*(Sh+-1*St*b))
var b
  solution
  (b.(-1*St^2+St2*period)^-1*(-1*Sh*St+Sth*period))

For vertical, replace h th by v tv
*/
        return (Sth*period - Sh*St) / (St2*period - St*St);
        }

    private double acceleration(double St, double St2, double St3, double St4, double Sh, double Sth, double St2h, double period)
        {

/*

acceleration c 

(horizontal)

accumulate (S) t t2 t3 t4 h th t2h
(vertical                 v tv t2v )

solution
( c
.     ( -1*St2^3
    + 2*St*St2*St3
    + St2*St4*period
    + -1*St^2*St4
    + -1*St3^2*period
    )
  ^ -1
* ( -1*Sh*St2^2
  + Sh*St*St3
  + St*St2*Sth
  + St2*St2h*period
  + -1*St3*Sth*period
  + -1*St^2*St2h
  )
)

For vertical, replace h th t2h by v tv t2v
*/
        return 
        ( Sh*(St*St3 - St2*St2)
        + Sth*(St*St2 - St3*period)
        + St2h*(St2*period - St*St)
        )
      /
        ( St2*(2*St*St3 - St2*St2 + St4*period)
        - St*St*St4
        - St3*St3*period
        );

        }
            
    private double simplejerk_d(double St2, double St2h, double St3, double St3h, double St4, double St5, double St6, double Sth, double period)
        {
        double var1 = St3*St3;
        double var2 = St4*St4;
        double var5 = St3*St5;
        double var6 = var1-St2*St4;

        return -(St2h*(St2*St5*period-St3*(St2*St2+St4*period))+St3h*(St2*St2*St2+period*var6)+Sth*(St2*var6+period*(var2-var5)))
           /(var1*var1-St2*(St5*St5*period-St2*(var2+2*var5-St2*St6)+St4*(3*var1-St6*period))-period*(St6*var1+St4*(var2+-2.0*var5)));
        }
        
    private void setVectorSizePerUnitOfHeadSize()
        {
        if(headsize > 5.0) // very small head!
            vectorSizePerUnitOfHeadSize =  100.0 / headsize;
        squareOfHeadSizePerUnitOfVectorSize = 1.0/(vectorSizePerUnitOfHeadSize * vectorSizePerUnitOfHeadSize);
        }

    public static Mat bufferedImageToMat(BufferedImage bi)
        {
        Mat mat = new Mat(bi.getHeight(),bi.getWidth(),CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        mat.put(0,0,data);
        return mat;
        }
    /**
    * Listens to media time changes.
    */
    public void mediaTimeChanged(MediaTimeChangeEvent e)
        {
        if (!disableFaceTracking) 
            {
            Time time = e.getTime();
            if(framenr == -1)
                framenr = mediaDelegate.mapTimeToFrame(time);



            int point = (int) (time.getSeconds() * 100 / mediaDelegate.getVideoDuration().getSeconds());
            if (videoWindow == null) 
                {
                videoWindow =  (JFrame)main.getVideoWindow();
                }
            Dimension size = videoWindow.getSize();
            int horSizeVideo = size.width;
            if (horSizeVideo < 0) 
                {
                horSizeVideo = 0;
                }
            int verSizeVideo = size.height;
            if (verSizeVideo < 0) 
                {
                verSizeVideo = 0;
                }

            if (overlayPanel == null) 
                {
                overlayPanel = main.getVideoOverlay();
                horminClip = 0;
                verminClip = 0;
                hormaxClip = overlayPanel.getWidth();
                vermaxClip = overlayPanel.getHeight();
                overlayPanel.addMouseListener(this);
                g = overlayPanel.getGraphics();
                }
            overlayPanelHorSize = overlayPanel.getWidth();
            overlayPanelVerSize = overlayPanel.getHeight();
            horOffsetOverlayPanel = horSizeVideo - overlayPanelHorSize;
            verOffsetOverlayPanel = verSizeVideo - overlayPanelVerSize;
            clipCorrect();
            int horSizeClip = hormaxClip - horminClip;
            int verSizeClip = vermaxClip - verminClip;
            BufferedImage image = new BufferedImage(horOffsetOverlayPanel + hormaxClip, verOffsetOverlayPanel + vermaxClip, BufferedImage.TYPE_3BYTE_BGR/*TYPE_INT_RGB*/);
            
            /* Only create an image that contains (in the bottom right corner)
            the face we are trying to localise. */

            overlayPanel.clearShapes(); /* remove dots (does not remove path 
                                        (square indicating clip area)).*/
            // Otherwise the red dot sits in the image to be analysed! 
            // (Normally that is apparently no problem for CV face detection!)
            // But still..
            overlayPanel.setPath("arrow", typa, path0);

            /* The next statement copies the contents of
            'videoWindow' to 'image', not the other way! */
            videoWindow.paint(image.getGraphics());
            /*
            try 
                {
                File outputfile = new File("saved.png");

                ImageIO.write(image, "png", outputfile);
                }
            catch (IOException ee) 
                {
                System.out.println("Cannot write");
                }
            */
            g.drawArc(xell, yell, well, hell, 0/*int startAngle*/, 360/*int arcAngle*/);
            overlayPanel.paintComponent(g);
            try 
                {
                // grab a new frame
                // Hack: restrict face recognition to the part of the image where 
                // there likely are any faces.

				Mat grabbedImage = bufferedImageToMat(image);

                //org.opencv.imgcodecs.Imgcodecs.imwrite("grabbedsaved.png",grabbedImage);

                //System.out.println("H:" + (horOffsetOverlayPanel + horminClip) + " ," + horSizeClip + " [" + grabbedImage.cols() + "]");
                //System.out.println("V:" + (verOffsetOverlayPanel + verminClip) + " ," + verSizeClip + " [" + grabbedImage.rows() + "]");


                Mat grabbedSubImage = grabbedImage.submat(verOffsetOverlayPanel + verminClip, verOffsetOverlayPanel + verminClip + verSizeClip, horOffsetOverlayPanel + horminClip, horOffsetOverlayPanel + horminClip + horSizeClip);

                //org.opencv.imgcodecs.Imgcodecs.imwrite("grabbedsubsaved.png",grabbedSubImage);

                headsize = horSizeClip + verSizeClip;
                setVectorSizePerUnitOfHeadSize();

                /* put origin in horminClip,verminClip:
                Face coordinates are same as overlay coordinates.
                */

	            Mat grayImage = new Mat();
                org.opencv.imgproc.Imgproc.cvtColor(grabbedSubImage, grayImage, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);

                //org.opencv.imgcodecs.Imgcodecs.imwrite("grayImage.png",grayImage);

                if ( classifier.empty() )
                    {
                    System.err.println("classifier EMPTY!");
                    System.exit(1);
                    }

                MatOfRect faces = new MatOfRect();
		        classifier.detectMultiScale(/*grabbedSubImage*/grayImage,faces);
		            
                java.awt.Point p0 = new java.awt.Point();
                java.awt.Point p1 = new java.awt.Point();
                java.awt.Point p2 = new java.awt.Point();
                java.awt.Point p3 = new java.awt.Point();
                java.awt.Point path[] = {p0, p1, p2, p3, p0};
                int clipSizeIncrement = 8;
                int clipSizeMargin = 0;
                if (faces.total() == 0) 
                    {
                    /*
                    if(faceSeen)
						{
						int temp = (hormaxClip - horminClip) / 2;
						if(temp < 1)
							temp = 1;
						horminClip -= temp;
						hormaxClip += temp;
						temp = (vermaxClip - verminClip) / 2;
						if(temp < 1)
							temp = 1;
						verminClip -= temp;
						vermaxClip += temp;
						}
					else*/
						{
						horminClip -= clipSizeIncrement;
						verminClip -= clipSizeIncrement;
						hormaxClip += clipSizeIncrement;
						vermaxClip += clipSizeIncrement;
						}
                    clipCorrect();
                    }
                else 
                    {
                    int HorminFace = 10000;
                    int HormaxFace = 0;
                    int VermaxFace = 0;
                    int VerminFace = 10000;
                    int done = 0;
                    int faceHorCenter = 0;
                    int faceVerCenter = 0;
                    int faceHorCenter2 = 0;
                    int faceVerCenter2 = 0;
                    int withinBounds = 0;
                    faceSeen = true;
                    Rect[] rects = faces.toArray();
                    for (int faceIndex = 0; faceIndex < rects.length; faceIndex++) 
                        {
                        Rect rrr = rects[faceIndex];
                        int x = horminClip+rrr.x, y = verminClip+rrr.y, w = rrr.width, hhh = rrr.height;

                        if (x > horminClip
                            && x + w < horminClip + horSizeClip
                            && y > verminClip
                            && y + hhh < verminClip + verSizeClip) 
                            {
                            ++withinBounds;
                            if (y < VerminFace) 
                                {
                                VerminFace = y;
                                }
                            if (y + hhh > VermaxFace) 
                                {
                                VermaxFace = y + hhh;
                                }
                            if (x < HorminFace) 
                                {
                                HorminFace = x;
                                }
                            if (x + w > HormaxFace) 
                                {
                                HormaxFace = x + w;
                                }
                            faceHorCenter2 = 2*x + w;
                            faceHorCenter = x + w / 2;
                            faceVerCenter2 = 2*y + hhh;
                            faceVerCenter = y + hhh / 2;
                            /* Check that the center of the current 
                             * face isn't inside the bounding box of 
                             * a preceding face.
                             */
                            int faceIndexPreceding;
                            for (faceIndexPreceding = 0; faceIndexPreceding < faceIndex; faceIndexPreceding++) 
                                {
								                //CvRect rp = new CvRect(cvGetSeqElem(faces, faceIndexPreceding));
                                Rect rp = rects[faceIndexPreceding];
                                int xp = rp.x, yp = rp.y, wp = rp.width, hp = rp.height;
                                if (faceHorCenter > xp
                                    && faceHorCenter < xp + wp
                                    && faceVerCenter > yp
                                    && faceVerCenter < yp + hp) 
                                    {
                                    break;
                                    }
                                }

                            if (faceIndexPreceding == faceIndex) // no overlap                                
                                {
                                ++done;
                                /* Put a (red) marker on the screen for
                                 * as long as there are not enough frames
                                 * to compute face movements.
                                 */
                                if (index < period) 
                                    {
                                    overlayPanel.setShape("shape" + Integer.toString(done), faceHorCenter, faceVerCenter);
                                    }
                                int ind = index % seqsiz;

                                if (index == 0) 
                                    // Start accumulation of datapoints
                                    {
                                    t0 = time.getSeconds();
                                    ts[0] = 0.0;
                                    }
                                else 
                                    {
                                    ts[ind] = time.getSeconds() - t0;
                                    }

                                Ts[ind] = time;
                                hs[ind] = faceHorCenter2;
                                vs[ind] = faceVerCenter2;
                                ++index;
                                if (index >= period) 
                                    {
                                    /*
                                     * Regression
                                     */
                                    int i;
                                    double medianTime = 0;//(ts[ind] + ts[(index - period) % seqsiz]) / 2.0; // rounded to integer

                                    double St=0.0, St2=0.0, St3=0.0, St4=0.0, St5=0.0, St6=0.0, 
                                           Sh=0.0, Sth=0.0, St2h=0.0, St3h=0.0, 
                                           Sv=0.0, Stv=0.0, St2v=0.0, St3v=0.0;
                                    double avarageh = 0;
                                    double avaragev = 0;
                                    for(i = index - period;i < index;++i)
                                        {
                                        int id = i % seqsiz;
                                        avarageh += hs[id];
                                        avaragev += vs[id];
                                        medianTime += ts[id];
                                        }
                                    avarageh = avarageh / period;
                                    avaragev = avaragev / period;
                                    medianTime = medianTime / period;
                                    for(i = index - period;i < index;++i)
                                        {
                                        int id = i % seqsiz;
                                        double t = ts[id] - medianTime;
                                        double t2 =  t * t;
                                        double t3 =  t2 * t;
                                        double h = hs[id] - avarageh;
                                        double v = vs[id] - avaragev;
                                        St   += t;
                                        St2  += t2;
                                        St3  += t2*t;
                                        St4  += t2*t2;
                                        St5  += t2*t3;
                                        St6  += t3*t3;
                                        Sh   += h;
                                        Sth  += t*h;
                                        St2h += t2*h;
                                        St3h += t3*h;
                                        Sv   += v;
                                        Stv  += t*v;
                                        St2v += t2*v;
                                        St3v += t3*v;
                                        }
                                    /*
                                     * Average position of the head 
                                     * during the period.
                                     */
                                    H = (int)((avarageh + (Sh / (double)period))/2.0);
                                    V = (int)((avaragev + (Sv / (double)period))/2.0);

                                    /*
                                     * Compute position and dimensions of
                                     * the ellipse that appears in the
                                     * overlay panel when the user pulls
                                     * the horizontal or vertical slider.
                                     */
                                    xell = H - hthres.ellipseRadius;
                                    yell = V - vthres.ellipseRadius;
                                    well = 2 * hthres.ellipseRadius;
                                    hell = 2 * vthres.ellipseRadius;
                                    
                                    if(doAcceleration)
                                        {
                                        double ah = acceleration(St, St2, St3, St4, Sh, Sth, St2h, (double)period);
                                        double av = acceleration(St, St2, St3, St4, Sv, Stv, St2v, (double)period);
                                        acceleration.ArrowStuff(ah,av,ind,"acceleration",Color.blue,Color.cyan);
                                        }

                                    if(doVelocity)
                                        {
                                        double vh = velocity(St, St2, Sh, Sth, (double)period);
                                        double vv = velocity(St, St2, Sv, Stv, (double)period);
                                        velocity.ArrowStuff(vh,vv,ind,"velocity",Color.red,Color.orange);
                                        }
                                        
                                    if(doJerk)
                                        {
                                        double ch = simplejerk_d(St2, St2h, St3, St3h, St4, St5, St6, Sth, period);
                                        double cv = simplejerk_d(St2, St2v, St3, St3v, St4, St5, St6, Stv, period);
                                        //double ch = jerk_d(Sh, St, St2, St2h, St3, St3h, St4, St5, St6, Sth, (double)period);
                                        //double cv = jerk_d(Sv, St, St2, St2v, St3, St3v, St4, St5, St6, Stv, (double)period);
                                        jerk.ArrowStuff(ch,cv,ind,"jerk",Color.pink,Color.green);
                                        }
                                    }
                                else
									{
                                    /*Not enough data collected to fill a period.*/
									}
                                
                                }
                            /*
                            else
                                Current face overlaps with
                                a preceding face. Discard.
                            */
                            }
                        else 
                            {
                            }
                        faceIndex++;
                        }
                    if (withinBounds > 0) 
                        {
                        if (prevNumberOfFaces > done) // Missing faces? Increase clip area
                            {
                            if (horminClip > HorminFace - clipSizeIncrement) 
                                {
                                horminClip = HorminFace - clipSizeIncrement;
                                }
                            if (hormaxClip < HormaxFace + clipSizeIncrement) 
                                {
                                hormaxClip = HormaxFace + clipSizeIncrement;
                                }
                            if (vermaxClip < VermaxFace + clipSizeIncrement) 
                                {
                                vermaxClip = VermaxFace + clipSizeIncrement;
                                }
                            if (verminClip > VerminFace - clipSizeIncrement) 
                                {
                                verminClip = VerminFace - clipSizeIncrement;
                                }
                            clipCorrect();
                            }
                        else 
                            {
                            clipSizeMargin = HormaxFace - HorminFace;
                            if (VermaxFace - VerminFace > clipSizeMargin) 
                                {
                                clipSizeMargin = VermaxFace - VerminFace;
                                }
                            clipSizeMargin /= 4;
                            horminClip = HorminFace - clipSizeMargin;
                            hormaxClip = HormaxFace + clipSizeMargin;
                            vermaxClip = VermaxFace + clipSizeMargin;
                            verminClip = VerminFace - clipSizeMargin;
                            }
                        }
                    else 
                        {
                        horminClip -= clipSizeIncrement / 8;
                        verminClip -= clipSizeIncrement / 8;
                        hormaxClip += clipSizeIncrement / 8;
                        vermaxClip += clipSizeIncrement / 8;
                        clipCorrect();
                        }
                    prevNumberOfFaces = done;
                    oldtime = time;
                    }
                p0.x = horminClip;
                p0.y = verminClip;
                p1.x = hormaxClip;
                p1.y = verminClip;
                p2.x = hormaxClip;
                p2.y = vermaxClip;
                p3.x = horminClip;
                p3.y = vermaxClip;
                overlayPanel.setPath("faces", typ, path);
                }
            catch (java.lang.NegativeArraySizeException nas) 
                {
                System.out.println("horOffsetOverlayPanel:" + horOffsetOverlayPanel
                    + " verOffsetOverlayPanel:" + verOffsetOverlayPanel
                    + " horSizeVideo:" + horSizeVideo
                    + " verSizeVideo:" + verSizeVideo
                    + " horminClip:" + horminClip
                    + " verminClip:" + verminClip
                    + " hormaxClip" + hormaxClip
                    + " vermaxClip:" + vermaxClip);
                System.out.println("horOffsetOverlayPanel+horminClip:"
                    + (horOffsetOverlayPanel + horminClip)
                    + " verOffsetOverlayPanel+verminClip:"
                    + (verOffsetOverlayPanel + verminClip)
                    + " hormaxClip-horminClip:" + (hormaxClip - horminClip)
                    + " vermaxClip-verminClip:" + (vermaxClip - verminClip));
                clipCorrect();
                }
            if(stepping && framenr >= 0)
                {                
                prevframenr = framenr;
                framenr += 1;
                mediaDelegate.setVideoFrame(framenr);
                }    
            }
        }
    }
