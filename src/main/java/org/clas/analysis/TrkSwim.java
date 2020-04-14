package org.clas.analysis;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.jlab.clas.swimtools.MagFieldsEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.clas.swimtools.Swimmer;
import org.jlab.geom.prim.Vector3D;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.io.task.DataSourceProcessorPane;
import org.jlab.io.task.IDataEventListener;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Point3D;

public class TrkSwim implements IDataEventListener {

    JPanel                  mainPanel 	= null;
    DataSourceProcessorPane processorPane 	= null;
    private JTabbedPane     tabbedPane      = null;

    private EmbeddedCanvas can1 = null;
    //Define histos


    int counter = 0;
    int updateTime = 100;


    public TrkSwim() {
        // create main panel
        mainPanel = new JPanel();	
        mainPanel.setLayout(new BorderLayout());

        tabbedPane 	= new JTabbedPane();

        processorPane = new DataSourceProcessorPane();
        processorPane.setUpdateRate(10);

        mainPanel.add(tabbedPane);
        mainPanel.add(processorPane,BorderLayout.PAGE_END);

        this.processorPane.addEventListener(this);
        createHistograms();
        createCanvas();
        addCanvasToPane();
        init();

    }


    private void createHistograms() {


    }

    private void createCanvas() {
            can1 = new EmbeddedCanvas(); can1.initTimer(updateTime);
            can1.divide(6, 2); 

    }
    private double solScale = -1.0;
    private double torScale = -1.0;
    private double shift = -1.9;
    private Swim swimmer;
    private double[] d_cm;
    private Vector3D[] n;
    private int direction = 1;
    int fmtNLayers;
    double[] fmtZ     ; // z position of the FMT layers in mm
    double[] fmtAngle ; // strip angle in deg

    private void init() {
        MagFieldsEngine mf = new MagFieldsEngine();
        mf.initializeMagneticFields();
        Swimmer.setMagneticFieldsScales(solScale, torScale, shift);
        swimmer = new Swim();
        // === SET GEOMETRY FROM DB ========================================================================
        // Set geometry parameters reading from database
        DatabaseConstantProvider dbProvider = new DatabaseConstantProvider(10, "rgf_spring2020");
        dbProvider.loadTable("/geometry/fmt/fmt_layer_noshim");
        fmtNLayers = 3;
        fmtZ     = new double[fmtNLayers]; // z position of the FMT layers in mm
        fmtAngle = new double[fmtNLayers]; // strip angle in deg
        d_cm = new double[fmtNLayers]; 
        for (int i=0; i<fmtNLayers; i++) {
            fmtZ[i]     = dbProvider.getDouble("/geometry/fmt/fmt_layer_noshim/Z",i);
            fmtAngle[i] = dbProvider.getDouble("/geometry/fmt/fmt_layer_noshim/Angle",i);
            d_cm[i] = fmtZ[i]/10;
            System.out.println("distance to plane ["+i+"] = "+(float) d_cm[i]);
        }
        
        n = new Vector3D[fmtNLayers];
        for(int i = 0; i<fmtNLayers; i++) {
            n[i] = new Vector3D(0,0,1);
        }

    }

    @Override
    public void dataEventAction(DataEvent event) {
        counter++; 
        HipoDataEvent hipo = (HipoDataEvent) event;      
        process(hipo);

    }

    @Override
    public void timerUpdate() {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void resetEventListener() {
        counter=0;
        this.init();
   }
    Vector3D p = new Vector3D(0, 0, 0);
    private void process(DataEvent event) {
        //
        //
        // Get relevant data banks.
        DataBank recRun;
        DataBank recTrajEB = null;
        DataBank fmtHits = null;
        DataBank fmtClusters = null;
        DataBank recParticles = null;
        if (event.hasBank("RUN::config"))      recRun = event.getBank("RUN::config");
        if (event.hasBank("FMTRec::Hits"))     fmtHits = event.getBank("FMTRec::Hits");
        if (event.hasBank("FMTRec::Clusters")) fmtClusters = event.getBank("FMTRec::Clusters");
        if (event.hasBank("REC::Traj"))        recTrajEB = event.getBank("REC::Traj");
        if (event.hasBank("REC::Particle"))    recParticles = event.getBank("REC::Particle");

        // Ignore events that don't have the necessary banks.
        if (fmtHits==null || fmtClusters==null || recTrajEB==null || recParticles==null) 
            return;

        // Loop through trajectory points.
        for (int loop=0; loop<recTrajEB.rows(); loop++) {
            int detector = recTrajEB.getByte("detector", loop);
            int layer    = recTrajEB.getByte("layer", loop);

            // Use only FMT layers 1,2,3 (ignore 4,5,6 that are not installed in RG-F).
            if (detector!=DetectorType.FMT.getDetectorId() || layer<1 || layer>fmtNLayers) continue;

            // === TRANSLATE TRAJECTORY USING SWIMMER ==================================================
            // Get relevant data.
            double swim_x  = (float) recParticles.getFloat("vx", loop);
            double swim_y  = (float) recParticles.getFloat("vy", loop);
            double swim_z  = (float) recParticles.getFloat("vz", loop);
            double swim_px = (float) recParticles.getFloat("px", loop);
            double swim_py = (float) recParticles.getFloat("py", loop);
            double swim_pz = (float) recParticles.getFloat("pz", loop);
            int swim_q     = (int)    recParticles.getByte("charge", loop);
            p.setXYZ(swim_px, swim_py, swim_pz);
            
            //cuts
            if(Math.abs(swim_q)!=1 || Double.isNaN(swim_x) || Double.isNaN(swim_y)
                     || Double.isNaN(swim_z) || Double.isNaN(swim_px) 
                    || Double.isNaN(swim_py) || Double.isNaN(swim_pz)  
                    || d_cm[layer-1]<swim_z)
                continue;
            Vector3D p = new Vector3D(swim_px, swim_py, swim_pz);
           if(Math.toDegrees(p.asUnit().theta())>45 || Math.toDegrees(p.asUnit().theta())<5) 
                continue;
            
            swimmer.SetSwimParameters(swim_x, swim_y, swim_z, swim_px, swim_py, swim_pz, swim_q);

            double[] V = swimmer.SwimToPlaneBoundary(d_cm[layer-1], n[layer-1], direction);
            System.out.println(" start "+new Point3D(swim_x,swim_y,swim_z).toString()+" end "+new Point3D(V[0],V[1],V[2]).toString());
            // === TRANSLATE TRAJECTORY AS A STRAIGHT LINE =============================================
            // Get DC track intersection with the FMT layer.
            double traj_x  = recTrajEB.getFloat("x", loop);
            double traj_y  = recTrajEB.getFloat("y", loop);
            double traj_z  = recTrajEB.getFloat("z", loop);
            double traj_cx = recTrajEB.getFloat("cx", loop);
            double traj_cy = recTrajEB.getFloat("cy", loop);
            double traj_cz = recTrajEB.getFloat("cz", loop);

            // Translate trajectory.
            double t = -5/traj_cz;
            traj_x = traj_x + traj_cx*t;
            traj_y = traj_y + traj_cy*t;
            double phiRef = fmtAngle[layer-1];
            double zRef   = fmtZ[layer-1]/10; //convert to cm
            // =========================================================================================

            // // Ignore track intersection that are too close to (0,0) or are not on the FMT layer (this
            // // shouldn't happen and has to be fixed in the trajectory bank).
            // if (Math.abs(z-zRef)>0.05 || (Math.abs(x)<3 && Math.abs(y)<3)) continue;
            //
            // // Rotate (x,y) to local coordinates.
            // double xLoc = x * Math.cos(Math.toRadians(phiRef)) + y * Math.sin(Math.toRadians(phiRef));
            // double yLoc = y * Math.cos(Math.toRadians(phiRef)) - x * Math.sin(Math.toRadians(phiRef));
            //
            // // Loop over the clusters and calculate residuals for every track-cluster combination.
            // for (int i=0; i<fmtClusters.rows(); i++) {
            //     // Check that the cluster layer matches the trajectory layer
            //     if (layer!=fmtClusters.getByte("layer", i)) continue;
            //     int strip     = fmtClusters.getInt("seedStrip", i);
            //     double yclus  = fmtClusters.getFloat("centroid", i);
            //     double energy = fmtClusters.getFloat("ETot", i);
            //
            //     // apply minimal cuts (to be optimized)
            //     if (strip<0 || energy<=50) continue;
            //     for (int c=0; c<nCanvas; ++c) {
            //         dgFMT[c].getH1F("hi_cluster_res_l" + layer).fill(yLoc - yclus);
            //         dgFMT[c].getH2F("hi_cluster_res_strip_l" + layer).fill(yLoc - yclus, strip);
            //     }
            // }
        }

            //

        
    }
    
   
    private void drawPlots() {
        
        can1.update();
	}

	private void addCanvasToPane() {
		tabbedPane.add("", can1);
		
	}

    public static void main(String[] args) {
        JFrame frame = new JFrame("FMT ANALYSIS");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Dimension screensize = null;
        screensize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize((int) (screensize.getHeight() * .75 * 1.618), (int) (screensize.getHeight() * .75));
        TrkSwim viewer = new TrkSwim();
        viewer.init();
        //frame.add(viewer.getPanel());
        frame.add(viewer.mainPanel);
        frame.setVisible(true);

    }

}
		


