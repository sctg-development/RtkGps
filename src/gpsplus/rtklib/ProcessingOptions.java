package gpsplus.rtklib;

import android.util.Log;

import gpsplus.rtkgps.BuildConfig;
import gpsplus.rtklib.RtkCommon.Position3d;
import gpsplus.rtklib.constants.Constants;
import gpsplus.rtklib.constants.EarthTideCorrectionType;
import gpsplus.rtklib.constants.EphemerisOption;
import gpsplus.rtklib.constants.IonosphereOption;
import gpsplus.rtklib.constants.NavigationSystem;
import gpsplus.rtklib.constants.PositioningMode;
import gpsplus.rtklib.constants.StationPositionType;
import gpsplus.rtklib.constants.TroposphereOption;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;


/**
 * Processing options prcopt_t
 *
 */
public class ProcessingOptions {

    private static final boolean DBG = BuildConfig.DEBUG & true;

    static final String TAG = ProcessingOptions.class.getSimpleName();

    /**
     * SNR mask type snr_t
     *
     */
    public static class SnrMask {

        // Used in native code
        private boolean enableRover;
        private boolean enableBase;

        /** mask (dBHz) at 5,10,...85 deg */
        // Used in native code
        private final double maskL1[];
        private final double maskL2[];
        private final double maskL5[];

        public SnrMask() {
            enableRover = enableBase = false;
            maskL1 = new double[9];
            maskL2 = new double[9];
            maskL5 = new double[9];
        }

        public void setValues(SnrMask src) {
            enableRover = src.enableRover;
            enableBase = src.enableBase;
            System.arraycopy(src.maskL1, 0, maskL1, 0, maskL1.length);
            System.arraycopy(src.maskL2, 0, maskL2, 0, maskL2.length);
            System.arraycopy(src.maskL5, 0, maskL5, 0, maskL5.length);
        }

        public void setSnrMask(int mask) {
            if (mask <= 0) {
                enableRover = enableBase = false;
                mask = 0;
            }else {
                enableRover = enableBase = true;
            }

            Arrays.fill(maskL1, mask);
            Arrays.fill(maskL2, mask);
            Arrays.fill(maskL5, mask);
        }

        public int getSnrMask() {
            if (!enableRover) return 0;
            return ((int)maskL1[0] / 5) * 5;
        }
    }

    static class Native {

        /** positioning mode (PMODE_???) */
        private int mode;

        /** solution type (0:forward,1:backward,2:combined) */
        private int soltype;

        /** number of frequencies (1:L1,2:L1+L2,3:L1+L2+L5) */
        private int nf;

        /** navigation system */
        private int navsys;

        /** elevation mask angle (rad) */
        private double elmin;

        /** SNR mask */
        private final SnrMask snrmask;

        /** satellite ephemeris/clock (EPHOPT_???) */
        private int sateph;

        /** AR mode (0:off,1:continuous,2:instantaneous,3:fix and hold) */
        private int modear;

        /** GLONASS AR mode (0:off,1:on,2:auto cal,3:ext cal) */
        private int glomodear;

	/** rtkexplorer stuff */

	/** GPS AR mode (o:off,1:on)  */
	private int gpsmodear;

	/** BeiDou AR mode (0:off,1:on)  */
	private int bdsmodear;

	/** AR filtering to reject bad sats (0:off,1:on)  */
	private int arfilter;

    private int minfixsats;    /* min sats to fix integer ambiguities */

	private int minholdsats;    /* min sats to hold integer ambiguities */

	private int mindropsats;    /* min sats to drop sats in AR */

	private int rcvstds;        /* use stdev estimates from receiver to adjust measurement variances */

	private int armaxiter;      /* max iteration to resolve ambiguity */

	private double varholdamb;  /* variance for fix-and-hold psuedo measurements (cycle^2) */

	private double gainholdamb; /* gain used for GLO and SBAS sats to adjust ambiguity */

	private int  maxaveep;      /* max averaging epoches */

	private int  initrst;       /* initialize by restart */

	private int  outsingle;     /* output single by dgps/float/fix/ppp outage */

	private int  syncsol;       /* solution sync mode (0:off,1:on) */

	private int freqopt;        /* disable L2-AR */


        /** obs outage count to reset bias */
        private int maxout;

        /** min lock count to fix ambiguity */
        private int minlock;

        /** min fix count to hold ambiguity */
        private int minfix;

        /** ionosphere option (IONOOPT_???) */
        private int ionoopt;

        /** troposphere option (TROPOPT_???) */
        private int tropopt;

        /** dynamics model (0:none,1:velociy,2:accel) */
        private int dynamics;

        /** earth tide correction (0:off,1:solid,2:solid+otl+pole) */
        private int tidecorr;

        /** number of filter iteration */
        private int niter;

        /** code smoothing window size (0:none) */
        private int codesmooth;

        /** interpolate reference obs (for post mission)  */
        private int intpref;

        /** SBAS correction options */
        private int sbascorr;

        /** SBAS satellite selection (0:all) */
        private int sbassatsel;

        /** rover position for fixed mode */
        private int rovpos;

        /**
         * base position for relative mode
         *   (0:pos in prcopt,  1:average of single pos,
         *  2:read from file, 3:rinex header, 4:rtcm pos)
         */
        private int refpos;

        /** code/phase error ratio */
        private double eratioL1;
        private double eratioL2;
        private double eratioL5;

        /** measurement error factor */
        private double errPhaseA;
        private double errPhaseB;
        private double errPhaseC;
        private double errDopplerFreq;

        /** initial-state std */
        private double stdBias;
        private double stdIono;
        private double stdTrop;

        /** process-noise std */
        private double prnBias;
        private double prnIono;
        private double prnTrop;
        private double prnAcch;
        private double prnAccv;

        /** satellite clock stability (sec/sec) */
        private double sclkstab;

        /** AR validation threshold */
        private double thresar_0, thresar_1, thresar_2, thresar_3;

        /** elevation mask of AR for rising satellite (deg) */
        private double elmaskar;

        /** elevation mask to hold ambiguity (deg) */
        private double elmaskhold;

        /** slip threshold of geometry-free phase (m)  */
        private double thresslip;

        /** max difference of time (sec) */
        private double maxtdiff;

        /** reject threshold of innovation (m) */
        private double maxinno;

        /** reject threshold of gdop */
        private double maxgdop;

        /** baseline length constraint {const,sigma} (m) */
        private double baselineConst;
        private double baselineSigma;

        /** rover position for fixed mode {x,y,z} (ecef) (m) */
        private double ruX, ruY, ruZ;

        /** base position for relative mode {x,y,z} (ecef) (m) */
        private double baseX, baseY, baseZ;

        /** antenna types {rover,base} */
        private String anttypeBase, anttypeRover;

        /** antenna delta {{rov_e,rov_n,rov_u},{ref_e,ref_n,ref_u}} */
        private double antdelRovE, antdelRovN, antdelRovU;
        private double antdelRefE, antdelRefN, antdelRefU;

        /** XXX: receiver antenna parameters {rov,base} */
        // PcvT pcvrRover,pcvrBase;

        /** excluded satellites */
        private final boolean exsats[];

        /** rinex options */
        private String rnxoptBase, rnxoptRover;

        /** positioning options */
        private final int posopt[];

        /** XXX: extended receiver error model */
        // ExtErr exterr;

        public Native() {
            exsats = new boolean[Constants.MAXSAT];
            posopt = new int[6];
            snrmask = new SnrMask();
            _loadDefaults();
        }

        native void _loadDefaults();

    }

    private final Native mNative;

    public ProcessingOptions() {
        mNative = new Native();
    }

    Native getNative() {
        return mNative;
    }

    public void setValues(ProcessingOptions src) {
        mNative.mode = src.mNative.mode;
        mNative.soltype = src.mNative.soltype;
        mNative.nf = src.mNative.nf;
        mNative.navsys = src.mNative.navsys;
        mNative.elmin = src.mNative.elmin;
        mNative.snrmask.setValues(src.mNative.snrmask);
        mNative.sateph = src.mNative.sateph;
        mNative.modear = src.mNative.modear;
        mNative.glomodear = src.mNative.glomodear;

	mNative.gpsmodear = src.mNative.gpsmodear;
	mNative.bdsmodear = src.mNative.bdsmodear;
	mNative.arfilter = src.mNative.arfilter;
    mNative.minfixsats = src.mNative.minfixsats;
	mNative.minholdsats = src.mNative.minholdsats;
	mNative.mindropsats = src.mNative.mindropsats;
	mNative.rcvstds = src.mNative.rcvstds;
	mNative.armaxiter = src.mNative.armaxiter;
	mNative.varholdamb = src.mNative.varholdamb;
	mNative.gainholdamb = src.mNative.gainholdamb;
	mNative.maxaveep = src.mNative.maxaveep;
	mNative.initrst = src.mNative.initrst;
	mNative.outsingle = src.mNative.outsingle;
	mNative.syncsol = src.mNative.syncsol;
	mNative.freqopt = src.mNative.freqopt;


        mNative.maxout = src.mNative.maxout;
        mNative.minlock = src.mNative.minlock;
        mNative.minfix = src.mNative.minfix;
        mNative.ionoopt = src.mNative.ionoopt;
        mNative.tropopt = src.mNative.tropopt;
        mNative.dynamics = src.mNative.dynamics;
        mNative.tidecorr = src.mNative.tidecorr;
        mNative.niter = src.mNative.niter;
        mNative.codesmooth = src.mNative.codesmooth;
        mNative.intpref = src.mNative.intpref;
        mNative.sbascorr = src.mNative.sbascorr;
        mNative.sbassatsel = src.mNative.sbassatsel;
        mNative.rovpos = src.mNative.rovpos;
        mNative.refpos = src.mNative.refpos;
        mNative.eratioL1 = src.mNative.eratioL1;
        mNative.eratioL2 = src.mNative.eratioL2;
        mNative.eratioL5 = src.mNative.eratioL5;
        mNative.errPhaseA = src.mNative.errPhaseA;
        mNative.errPhaseB = src.mNative.errPhaseB;
        mNative.errPhaseC = src.mNative.errPhaseC;
        mNative.errDopplerFreq = src.mNative.errDopplerFreq;
        mNative.stdBias = src.mNative.stdBias;
        mNative.stdIono = src.mNative.stdIono;
        mNative.stdTrop = src.mNative.stdTrop;
        mNative.prnBias = src.mNative.prnBias;
        mNative.prnIono = src.mNative.prnIono;
        mNative.prnTrop = src.mNative.prnTrop;
        mNative.prnAcch = src.mNative.prnAcch;
        mNative.prnAccv = src.mNative.prnAccv;
        mNative.sclkstab = src.mNative.sclkstab;
        mNative.thresar_0 = src.mNative.thresar_0;
        mNative.thresar_1 = src.mNative.thresar_1;
        mNative.thresar_2 = src.mNative.thresar_2;
        mNative.thresar_3 = src.mNative.thresar_3;
        mNative.elmaskar = src.mNative.elmaskar;
        mNative.elmaskhold = src.mNative.elmaskhold;
        mNative.thresslip = src.mNative.thresslip;
        mNative.maxtdiff = src.mNative.maxtdiff;
        mNative.maxinno = src.mNative.maxinno;
        mNative.maxgdop = src.mNative.maxgdop;
        mNative.baselineConst = src.mNative.baselineConst;
        mNative.baselineSigma = src.mNative.baselineSigma;
        mNative.ruX = src.mNative.ruX;
        mNative.ruY = src.mNative.ruY;
        mNative.ruZ = src.mNative.ruZ;
        mNative.baseX =src.mNative.baseX;
        mNative.baseY = src.mNative.baseY;
        mNative.baseZ = src.mNative.baseZ;
        mNative.anttypeBase = src.mNative.anttypeBase;
        mNative.anttypeRover = src.mNative.anttypeRover;
        mNative.antdelRovE = src.mNative.antdelRovE;
        mNative.antdelRovN = src.mNative.antdelRovN;
        mNative.antdelRovU = src.mNative.antdelRovU;
        mNative.antdelRefE = src.mNative.antdelRefE;
        mNative.antdelRefN = src.mNative.antdelRefN;
        mNative.antdelRefU = src.mNative.antdelRefU;
        System.arraycopy(src.mNative.exsats, 0, mNative.exsats, 0, mNative.exsats.length);
        mNative.rnxoptBase = src.mNative.rnxoptBase;
        mNative.rnxoptRover = src.mNative.rnxoptRover;
        System.arraycopy(src.mNative.posopt, 0, mNative.posopt, 0, mNative.posopt.length);
    }

    public PositioningMode getPositioningMode() {
        return PositioningMode.valueOf(mNative.mode);
    }

    public ProcessingOptions setPositioningMode(PositioningMode mode) {
        this.mNative.mode = mode.getRtklibId();
        return this;
    }

    public void setAntTypeRover(String type){
        this.mNative.anttypeRover = type;
    }

    public String getAntTypeRover(){
        return this.mNative.anttypeRover;
    }

    public void setAntTypeBase(String type){
        this.mNative.anttypeBase = type;
    }

    public String getAntTypeBase(){
        return this.mNative.anttypeBase;
    }
    /**
     * get ambiguity resolution mode
     * (0:off,1:continuous,2:instantaneous,3:fix and hold)
     * @return
     */
    public int getModeAR(){
        return this.mNative.modear;
    }

    /**
     * set ambiguity resolution mode
     * @param mode (0:off,1:continuous,2:instantaneous,3:fix and hold)
     */
    public void setModeAR(int mode){
        this.mNative.modear = mode;
    }

    /**
     * get glonass ambiguity resolution mode
     * GLONASS AR mode (0:off,1:on,2:auto cal,3:ext cal)
     * @return
     */
    public int getModeGAR(){
        return this.mNative.glomodear;
    }

    /**
     * set glonass ambiguity resolution mode
     * @param mode GLONASS AR mode (0:off,1:on,2:auto cal,3:ext cal)
     */
    public void setModeGAR(int mode){
        this.mNative.glomodear = mode;
    }


    /**
     * get gps ambiguity resolution mode
     * GPS AR mode (0:off,1:on)
     * @return
     */
    public int getModeGpsAR(){
        return this.mNative.gpsmodear;
    }

    /**
     * set gps ambiguity resolution mode
     * @param mode GPS AR mode (0:off,1:on)
     */
    public void setModeGpsAR(int mode){
        this.mNative.gpsmodear = mode;
    }


    /**
     * get beidou ambiguity resolution mode
     * BDS AR mode (0:off,1:on)
     * @return
     */
    public int getModeBDSAR(){
        return this.mNative.bdsmodear;
    }

    /**
     * set beidou ambiguity resolution mode
     * @param mode BDS AR mode (0:off,1:on)
     */
    public void setModeBDSAR(int mode){
        this.mNative.bdsmodear = mode;
    }

    public void setArFilter(int filter){
        this.mNative.arfilter = filter;
    }
    public int getArFilter(){
        return this.mNative.arfilter;
    }

    public void setMinFixCountToHoldAmbiguity(int arminfix){
        this.mNative.minfix = arminfix;
    }
    public int getMinFixCountToHoldAmbiguity(){
        return this.mNative.minfix;
    }

    public void setMinFixToFixAmbiguity(int minfixsats){
        this.mNative.minfixsats = minfixsats;
    }
    public int getMinFixToFixAmbiguity(){   // fixme
        return this.mNative.minfixsats;
    }

    public void setMinHoldToFixAmbiguity(int minhold){
        this.mNative.minholdsats = minhold;
    }
    public int getMinHoldToFixAmbiguity(){
        return this.mNative.minholdsats;
    }

    public void setMinDropToFixAmbiguity(int mindrop){
        this.mNative.mindropsats = mindrop;
    }
    public int getMinDropToFixAmbiguity(){
        return this.mNative.mindropsats;
    }

    public void setRcvStds(int rcvs){
        this.mNative.rcvstds = rcvs;
    }
    public int getRcvStds(){
        return this.mNative.rcvstds;
    }

    public void setArMaxIter(int armaxiter){
        this.mNative.armaxiter = armaxiter;
    }
    public int getArMaxIter(){
        return this.mNative.armaxiter;
    }

    public void setNIter(int niter){
        this.mNative.niter = niter;
    }
    public int getNIter(){
        return this.mNative.niter;
    }

    public void setVarHoldAmb(double varhold){
        this.mNative.varholdamb = varhold;
    }
    public double getVarHoldAmb(){
        return this.mNative.varholdamb;
    }

    public void setGainHoldAmb(double gainhold){
        this.mNative.gainholdamb = gainhold;
    }
    public double getGainHoldAmb(){
        return this.mNative.gainholdamb;
    }

    public void setMaxAveEp(int mveep){
        this.mNative.maxaveep = mveep;
    }
    public int getMaxAveEp(){
        return this.mNative.maxaveep;
    }

    public void setInitRst(int rst){
        this.mNative.initrst = rst;
    }
    public int getInitRst(){
        return this.mNative.initrst;
    }

    public void setOutSingle(int outs){
        this.mNative.outsingle = outs;
    }
    public int getOutSingle(){
        return this.mNative.outsingle;
    }

    public void setSyncSol(int synsol){
        this.mNative.syncsol = synsol;
    }
    public int getSyncSol(){
        return this.mNative.syncsol;
    }

    public void setFreqOpt(int fopt){
        this.mNative.freqopt = fopt;
    }
    public int getFreqOpt(){
        return this.mNative.freqopt;
    }

    public void setArOutCnt(int cnt){
        this.mNative.maxout = cnt;
    }
    public int getArOutCnt(){
        return this.mNative.maxout;
    }

    public void setSlipThres(double slip){
        this.mNative.thresslip = slip;
    }
    public double getSlipThres(){
        return this.mNative.thresslip;
    }

    public void setMaxAge(double age){
        this.mNative.maxtdiff = age;
    }
    public double getMaxAge(){
        return this.mNative.maxtdiff;
    }

    public void setRejGDop(double rdop){
        this.mNative.maxgdop = rdop;
    }
    public double getRejGDop(){
        return this.mNative.maxgdop;
    }

    public void setRejIonno(double ion){
        this.mNative.maxinno = ion;
    }
    public double getRejIonno(){
        return this.mNative.maxinno;
    }

    /**
     * set the Min ratio to fix ambiguity
     * @param thres ratio
     */
    public void setValidThresoldAR(double thres){
        this.mNative.thresar_0 = thres;
    }

    /**
     * set the Max Position Variance
     * @param thres ratio getMaxPositionVariance
     */
    public void setMaxPositionVariance(double thres){
        this.mNative.thresar_1 = thres;
    }
    /**
     * get min ratio to fix ambiguity
     * @return
     */
    public double getValidThresoldAR(){
        return this.mNative.thresar_0;
    }
    public double getMaxPositionVariance() {
        return this.mNative.thresar_1;
    }

    public void setMinLockToFixAmbiguity(int minlock){
        this.mNative.minlock = minlock;
    }
    public int getMinLockToFixAmbiguity(){
        return this.mNative.minlock;
    }
    public void setMinElevationToFixAmbiguityRad(double minelevation){
        this.mNative.elmaskar = minelevation;
    }
    public double getMinElevationToHoldAmbiguityRad(){
        return this.mNative.elmaskhold;
    }
    public void setMinElevationToHoldAmbiguityRad(double mineleva){
        this.mNative.elmaskar = mineleva;
    }
    public double getMinElevationToFixAmbiguityRad(){
        return this.mNative.elmaskar;
    }

    public void setMinSnrToFixAmbiguity(double minsnr){
      //  this.mNative. = minsnr;
    }
    public double getMinSnrToFixAmbiguity(){
      //  return this.mNative.;
        return 0;
    }
    /**
     * @return nf (1:L1,2:L1+L2,3:L1+L2+L5)
     */
    public int getNumberOfFrequencies() {
        return mNative.nf;
    }

    /**
     * @param nf the nf to set (1:L1,2:L1+L2,3:L1+L2+L5)
     */
    public void setNumberOfFrequencies(int nf) {
        if (nf < 1 || nf > Constants.NFREQ) throw new IllegalArgumentException();
        this.mNative.nf = nf;
    }

    /**
     * @return selected navigation system set
     */
    public Set<NavigationSystem> getNavigationSystem() {
        EnumSet<NavigationSystem> res = EnumSet.noneOf(NavigationSystem.class);
        for (NavigationSystem ns: NavigationSystem.values()) {
            if ((mNative.navsys & ns.getRtklibId()) != 0) res.add(ns);
        }

        return res;
    }

    /**
     * @param nss Navigation system set
     */
    public void setNavigationSystem(Set<NavigationSystem> nss) {
        int res = 0;
        for (NavigationSystem ns: nss) {
            res |= ns.getRtklibId();
        }
        mNative.navsys = res;
        if (DBG) Log.v(TAG, "setNavigationSystem() navsys: %x" + mNative.navsys);
    }

    /**
     * @return elevation mask angle (rad)
     */
    public double getElevationMask() {
        return mNative.elmin;
    }

    /**
     * @param elevation mask angle (rad)
     */
    public void setElevationMask(double elmin) {
        this.mNative.elmin = elmin;
    }

    /**
     * @param SNR mask
     */
    public void setSnrMask(int mask) {
        this.mNative.snrmask.setSnrMask(mask);
    }

    /**
     * @return SNR mask
     */
    public int getSnrMask() {
        return this.mNative.snrmask.getSnrMask();
    }

    /**
     * @return Rec dynamics
     */
    public boolean getRecDynamics() {
        return this.mNative.dynamics != 0;
    }

    /**
     * @param on rec dynamics
     */
    public void setRecDynamics(boolean on) {
        this.mNative.dynamics = on ? 1 : 0;
    }

    /**
     * @return Earth tides correction
     */
    public EarthTideCorrectionType getEarthTidersCorrection() {
        return EarthTideCorrectionType.valueOf(this.mNative.tidecorr);
    }

    /**
     * @param on Earth tides correction
     */
    public void setEarthTidesCorrection(EarthTideCorrectionType type) {
        this.mNative.tidecorr = type.getRtklibId();
    }

    /**
     * @return Ionosphere correction
     */
    public IonosphereOption getIonosphereCorrection() {
        return IonosphereOption.valueOf(mNative.ionoopt);
    }

    /**
     * @param Set Ionosphere correction
     */
    public void setIonosphereCorrection(IonosphereOption corr) {
        mNative.ionoopt = corr.getRtklibId();
    }

    /**
     * @return Troposphere correction
     */
    public TroposphereOption getTroposphereCorrection() {
        return TroposphereOption.valueOf(mNative.tropopt);
    }

    /**
     * @param Set Troposphere correction
     */
    public void setTroposphereCorrection(TroposphereOption corr) {
        mNative.tropopt = corr.getRtklibId();
    }

    /**
     * @return Satellite Ephemeris/Clock
     */
    public EphemerisOption getSatEphemerisOption() {
        return EphemerisOption.valueOf(mNative.sateph);
    }

    /**
     *
     * @param Set satellite Ephemeris/Clock
     */
    public void setSatEphemerisOption(EphemerisOption opt) {
        this.mNative.sateph = opt.getRtklibId();
    }

    /**
     * @return satellite antenna model enabled
     */
    public boolean isSatAntennaPcvEnabled() {
        return mNative.posopt[0] != 0;
    }

    /**
     * @param enable satellite antenna model
     */
    public void setSatAntennaPcvEnabled(boolean enable) {
        mNative.posopt[0] = enable ? 1 : 0;
    }

    /**
     *
     * @return receiver antenna model enabled
     */
    public boolean isReceiverAntennaPcvEnabled() {
        return mNative.posopt[1] != 0;
    }

    /**
     *
     * @param enable receiver antenna model
     */
    public void setReceiverAntennaPcvEnabled(boolean enable) {
        mNative.posopt[1] = enable ? 1 : 0;
    }

    /**
     * @return phase windup correction enabled
     */
    public boolean isPhaseWindupCorrectionEnabled() {
        return mNative.posopt[2] != 0;
    }

    /**
     * @param enable phase windup correction
     */
    public void setPhaseWindupCorrectionEnabled(boolean enable) {
        mNative.posopt[2] = enable ? 1 : 0;
    }

    /**
     * @return measurements of eclipsing satellite excluded
     */
    public boolean isExcludeEclipsingSatMeasurements() {
        return mNative.posopt[3] != 0;
    }

    /**
     * @param exclude measurements of eclipsing satellite
     */
    public void setExcludeEclipsingSatMeasurements(boolean exclude) {
        mNative.posopt[3] = exclude ? 1 : 0;
    }

    /**
     * @return raim fde (failure detection and exclution) enabled
     */
    public boolean isRaimFdeEnabled() {
        return mNative.posopt[4] != 0;
    }

    /**
     *
     * @param enable raim fde (failure detection and exclution)
     */
    public void setRaimFdeEnabled(boolean enable) {
        mNative.posopt[4] = enable ? 1 : 0;
    }


    /**
     * @return Base station position type
     */
    public StationPositionType getBaseStationPositionType() {
        return StationPositionType.valueOf(mNative.refpos);
    }

    /**
     * @return ECEF base position
     */
    public Position3d getBaseStationPosition() {
        return new Position3d(mNative.baseX, mNative.baseY, mNative.baseZ);
    }

    public void setBasePosition(StationPositionType type, Position3d ecefPos) {
        mNative.refpos = type.getRtklibId();
        mNative.baseX = ecefPos.getX();
        mNative.baseY = ecefPos.getY();
        mNative.baseZ = ecefPos.getZ();
    }

    /**
     * @return Rover position type
     */
    public StationPositionType getRoverPositionType() {
        return StationPositionType.valueOf(mNative.rovpos);
    }

    /**
     * @return ECEF rover position
     */
    public Position3d getRoverPosition() {
        return new Position3d(mNative.ruX, mNative.ruY, mNative.ruZ);
    }

    public void setRoverPosition(StationPositionType type, Position3d ecefPos) {
        mNative.rovpos = type.getRtklibId();
        mNative.ruX = ecefPos.getX();
        mNative.ruY = ecefPos.getY();
        mNative.ruZ = ecefPos.getZ();
    }


}
