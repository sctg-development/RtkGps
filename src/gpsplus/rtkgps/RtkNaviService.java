package gpsplus.rtkgps;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

//import com.dropbox.sync.android.DbxAccountManager;
//import com.dropbox.sync.android.DbxException;
//import com.dropbox.sync.android.DbxException.Unauthorized;
//import com.dropbox.sync.android.DbxFile;
//import com.dropbox.sync.android.DbxFileSystem;
//import com.dropbox.sync.android.DbxPath;
//import com.dropbox.sync.android.DbxPath.InvalidPathException;

import gpsplus.rtkgps.settings.LogBaseFragment;
import gpsplus.rtkgps.settings.LogRoverFragment;
import gpsplus.rtkgps.settings.OutputGPXTraceFragment;
import gpsplus.rtkgps.settings.OutputSolution1Fragment;
import gpsplus.rtkgps.settings.OutputSolution2Fragment;
import gpsplus.rtkgps.settings.ProcessingOptions1Fragment;
import gpsplus.rtkgps.settings.SettingsHelper;
import gpsplus.rtkgps.settings.SolutionOutputSettingsFragment;
import gpsplus.rtkgps.settings.StreamBluetoothFragment;
import gpsplus.rtkgps.settings.StreamBluetoothFragment.Value;
import gpsplus.rtkgps.settings.StreamFileClientFragment;
import gpsplus.rtkgps.settings.StreamUsbFragment;
import gpsplus.rtkgps.utils.ZipHelper;
import gpsplus.rtklib.RtkCommon;
import gpsplus.rtklib.RtkCommon.Position3d;
import gpsplus.rtklib.RtkControlResult;
import gpsplus.rtklib.RtkServer;
import gpsplus.rtklib.RtkServerObservationStatus;
import gpsplus.rtklib.RtkServerSettings;
import gpsplus.rtklib.RtkServerSettings.TransportSettings;
import gpsplus.rtklib.RtkServerStreamStatus;
import gpsplus.rtklib.Solution;
import gpsplus.rtklib.constants.GeoidModel;
import gpsplus.rtklib.constants.StreamType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

public class RtkNaviService extends IntentService implements LocationListener
  {

    private class dpFile {
        private String localFilenameWithPath;
        private String remoteFilename;

        public dpFile(String localFilenameWithPath, String remoteFilename) {
            super();
            this.localFilenameWithPath = localFilenameWithPath;
            this.remoteFilename = remoteFilename;
        }
        @SuppressWarnings("unused")
        public dpFile(String filename) {
            super();
            this.localFilenameWithPath = filename;
            File _file = new File(filename);
            this.remoteFilename = _file.getName();
        }

        public String getLocalFilenameWithPath() {
            return localFilenameWithPath;
        }
        @SuppressWarnings("unused")
        public void setLocalFilenameWithPath(String localFilenameWithPath) {
            this.localFilenameWithPath = localFilenameWithPath;
        }
        public String getRemoteFilename() {
            return remoteFilename;
        }
        @SuppressWarnings("unused")
        public void setRemoteFilename(String remoteFilename) {
            this.remoteFilename = remoteFilename;
        }

    }
    public RtkNaviService() {
        super(RtkNaviService.class.getSimpleName());
        // TODO Auto-generated constructor stub
    }

    @SuppressWarnings("unused")
    private static final boolean DBG = BuildConfig.DEBUG & true;
    static final String TAG = RtkNaviService.class.getSimpleName();

    public static final String ACTION_START = "gpsplus.rtkgps.RtkNaviService.START";
    public static final String ACTION_STOP = "gpsplus.rtkgps.RtkNaviService.STOP";
    private static final String RTK_GPS_MOCK_LOCATION_SERVICE = "RtkGps mock location service";
    private static final String GPS_PROVIDER = LocationManager.GPS_PROVIDER;
    private int NOTIFICATION = R.string.local_service_started;
    private RtkCommon rtkCommon;

    // Binder given to clients
    private final IBinder mBinder = new RtkNaviServiceBinder();

    private static final RtkServer mRtkServer = new RtkServer();

    public static boolean mbStarted = false;
    private PowerManager.WakeLock mCpuLock;

    private BluetoothToRtklib mBtRover, mBtBase;
    private UsbToRtklib mUsbReceiver;
    private boolean mBoolIsRunning = false;
    private boolean mBoolLocationServiceIsConnected = false;
    private boolean mBoolMockLocationsPref = false;
    private Location mLocationPrec = null;
    private long mLStartingTime = 0;
    private boolean mBoolGenerateGPXTrace = false;
    private GPXTrace mGpxTrace = null;
    private long  mLProcessingCycle = 5;

    @Override
    public void onCreate() {
        super.onCreate();

        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mCpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.v(TAG, "RtkNaviService restarted");
            processStart();
        }else {
            final String action = intent.getAction();
            if (action.equals(ACTION_START)) processStart();
            else if(action.equals(ACTION_STOP)) processStop();
            else Log.e(TAG, "onStartCommand(): unknown action " + action);
        }
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return mBinder;
    }


    public final RtkServerStreamStatus getStreamStatus(
            RtkServerStreamStatus status) {
        return mRtkServer.getStreamStatus(status);
    }

    public final RtkServerObservationStatus getRoverObservationStatus(
            RtkServerObservationStatus status) {
        if (MainActivity.getDemoModeLocation().isInDemoMode() && mbStarted)
        {
            return MainActivity.getDemoModeLocation().getObservationStatus(status);
        }else{
            return mRtkServer.getRoverObservationStatus(status);
        }
    }

    public final RtkServerObservationStatus getBaseObservationStatus(
            RtkServerObservationStatus status) {
        return mRtkServer.getBaseObservationStatus(status);
    }

    public RtkControlResult getRtkStatus(RtkControlResult dst) {
        return mRtkServer.getRtkStatus(dst);
    }

    public static void loadSP3(String file) {
        if (mRtkServer != null)
            mRtkServer.readSP3(file);
    }

    public static void loadSatAnt(String file) {
        if (mRtkServer != null)
            mRtkServer.readSatAnt(file);
    }
    public boolean isServiceStarted() {
        return mRtkServer.getStatus() != RtkServerStreamStatus.STATE_CLOSE;
    }

    public int getServerStatus() {
        return mRtkServer.getStatus();
    }

    public Solution[] readSolutionBuffer() {
        return mRtkServer.readSolutionBuffer();
    }

    /**
     * Class used for the client Binder. Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class RtkNaviServiceBinder extends Binder {
        public RtkNaviService getService() {
            // Return this instance of UpdateDbService so clients can call
            // public methods
            return RtkNaviService.this;
        }
    }

    public void processStart() {
        final RtkServerSettings settings;

        mbStarted = true;
        try {
            MainActivity.getDemoModeLocation().reset();
            if (MainActivity.getDemoModeLocation().isInDemoMode())
            {
                MainActivity.getDemoModeLocation().startDemoMode();
            }
        } catch (NullPointerException e) {
            // TODO Auto-generated catch block
        }
        if (isServiceStarted()) return;

        settings = SettingsHelper.loadSettings(this);
        mRtkServer.setServerSettings(settings);

        mLStartingTime = System.currentTimeMillis();
        if (!mRtkServer.start()) {
            Log.e(TAG, "rtkSrvStart() error");
            return;
        }

        startBluetoothPipes();
        startUsb();

        mCpuLock.acquire();

        Notification notification = createForegroundNotification();
        startForeground(NOTIFICATION, notification);

        SharedPreferences prefs= this.getBaseContext().getSharedPreferences(SolutionOutputSettingsFragment.SHARED_PREFS_NAME, 0);
        mBoolMockLocationsPref = prefs.getBoolean(SolutionOutputSettingsFragment.KEY_OUTPUT_MOCK_LOCATION, false);
        prefs= this.getBaseContext().getSharedPreferences(OutputGPXTraceFragment.SHARED_PREFS_NAME, 0);
        mBoolGenerateGPXTrace = prefs.getBoolean(OutputGPXTraceFragment.KEY_ENABLE, false);
        prefs = this.getBaseContext().getSharedPreferences(ProcessingOptions1Fragment.SHARED_PREFS_NAME, 0);
        mLProcessingCycle  = Long.valueOf(prefs.getString(ProcessingOptions1Fragment.KEY_PROCESSING_CYCLE, "5"));
        if (mBoolMockLocationsPref)
        {
                if (Settings.Secure.getString(getContentResolver(),
                        Settings.Secure.ALLOW_MOCK_LOCATION).equals("0") )
                {
                    Log.e(RTK_GPS_MOCK_LOCATION_SERVICE,"Mock Location is not allowed");
                }else{
                 // Connect to Location Services
                    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    try {
                        locationManager.addTestProvider(GPS_PROVIDER, false, false,
                                false, false, true, false, true, 0, Criteria.ACCURACY_FINE);
                    } catch (IllegalArgumentException e) {
                        Log.e(RTK_GPS_MOCK_LOCATION_SERVICE,"Mock Location gps provider already exist");
                    }
                    locationManager.setTestProviderEnabled(GPS_PROVIDER, true);
                    locationManager.requestLocationUpdates(GPS_PROVIDER, 0, 0, this);

                    Log.i(RTK_GPS_MOCK_LOCATION_SERVICE,"Mock Location service was started");
                }
        }
        GeoidModel model = GeoidModel.valueOf( prefs.getString(SolutionOutputSettingsFragment.KEY_GEOID_MODEL,GeoidModel.EMBEDDED.name()) );
        rtkCommon = new RtkCommon(model);
        //load satellite antennas
        loadSatAnt(MainActivity.getApplicationDirectory()+File.separator+"files"+File.separator+"data"+File.separator+"igs05.atx");
        mBoolIsRunning = true;
    }


    private void processStop() {
        mBoolIsRunning = false;
        mbStarted = false;
        if (MainActivity.getDemoModeLocation().isInDemoMode())
        {
            MainActivity.getDemoModeLocation().stopDemoMode();
        }
        if (mBoolMockLocationsPref)
        {
            LocationManager lm = (LocationManager) getSystemService(
                    Context.LOCATION_SERVICE);
                  lm.removeTestProvider(GPS_PROVIDER);
        }
        stop();
        finalizeGpxTrace();
//        syncDropbox();
        stopSelf();
    }

    private void finalizeGpxTrace() {
        if (mBoolGenerateGPXTrace && (mGpxTrace != null))
        {
            SharedPreferences prefs= this.getBaseContext().getSharedPreferences(OutputGPXTraceFragment.SHARED_PREFS_NAME, 0);
//            if(prefs.getBoolean(OutputGPXTraceFragment.KEY_SYNCDROPBOX, false))
//                {
//                    String szFilename = prefs.getString(OutputGPXTraceFragment.KEY_FILENAME, "");
//                    if (szFilename.length()>0)
//                    {
//                        String szPath = MainActivity.getFileStorageDirectory() + File.separator + szFilename;
//                        mGpxTrace.writeFile(szPath);
//                    }
//
//                }
        }

    }

    private String getZipFilename(String filename)
    {
        return filename+".zip";
    }
/*
    private String insertDateTimeInDropboxFilename(String file)
    {
        File _file = new File(file);
        if (!file.contains("%Y") && !file.contains("%m")
                && !file.contains("%d")
                && !file.contains("%h")
                && !file.contains("%M")
                && !file.contains("%S"))
        {
            SimpleDateFormat sdtFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            sdtFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date dt = new Date(mLStartingTime);
            String szDate = sdtFormat.format(dt);
            int dotposition= _file.getName().lastIndexOf(".");
            String remoteFileName = _file.getName().substring(0, dotposition)+"-"+szDate+"."+_file.getName().substring(dotposition + 1, _file.getName().length());
            return remoteFileName;
        }else{
            return _file.getName();
        }
    }

    private void addToDropboxIfNeeded(SharedPreferences prefs, ArrayList<dpFile> alDropboxed, String file)
    {
        if(prefs.getBoolean(StreamFileClientFragment.KEY_SYNCDROPBOX, false)
                && prefs.getBoolean(StreamFileClientFragment.KEY_ENABLE, false))
            {
                if (prefs.getBoolean(StreamFileClientFragment.KEY_ZIPBEFORESYNCING, false))
                {
                    String[] _files = {file};
                    String zipFileName = getZipFilename(file);
                    ZipHelper.zip(_files, zipFileName);
                    String fileName = insertDateTimeInDropboxFilename(zipFileName);
                    alDropboxed.add(new dpFile(zipFileName,fileName));
                }else{
                    alDropboxed.add(new dpFile(file,insertDateTimeInDropboxFilename(file)));
                }
            }
    }
    private void syncDropbox()
    {
        DbxAccountManager mDbxAcctMgr;
        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), MainActivity.APP_KEY, MainActivity.APP_SECRET);
        if (mDbxAcctMgr.hasLinkedAccount())
        {
            ArrayList<dpFile> alDropboxed = new ArrayList<dpFile>();
            //Log rover
            if ( mRtkServer.getServerSettings().getLogRover().getType() == StreamType.FILE)
                {
                SharedPreferences prefs= this.getBaseContext().getSharedPreferences(LogRoverFragment.SHARED_PREFS_NAME, 0);
                addToDropboxIfNeeded(prefs, alDropboxed, mRtkServer.getServerSettings().getLogRover().getPath());
                }
            //Log base
            if ( mRtkServer.getServerSettings().getLogBase().getType() == StreamType.FILE)
            {
                SharedPreferences prefs= this.getBaseContext().getSharedPreferences(LogBaseFragment.SHARED_PREFS_NAME, 0);
                addToDropboxIfNeeded(prefs, alDropboxed, mRtkServer.getServerSettings().getLogBase().getPath());
            }
            //Solution 1
            if ( mRtkServer.getServerSettings().getOutputSolution1().getType() == StreamType.FILE)
            {
                SharedPreferences prefs= this.getBaseContext().getSharedPreferences(OutputSolution1Fragment.SHARED_PREFS_NAME, 0);
                addToDropboxIfNeeded(prefs, alDropboxed, mRtkServer.getServerSettings().getOutputSolution1().getPath());
            }
            //Solution 2
            if ( mRtkServer.getServerSettings().getOutputSolution2().getType() == StreamType.FILE)
            {
                SharedPreferences prefs= this.getBaseContext().getSharedPreferences(OutputSolution2Fragment.SHARED_PREFS_NAME, 0);
                addToDropboxIfNeeded(prefs, alDropboxed, mRtkServer.getServerSettings().getOutputSolution2().getPath());
            }
            //GPX Trace (not zippable)
            SharedPreferences prefs= this.getBaseContext().getSharedPreferences(OutputGPXTraceFragment.SHARED_PREFS_NAME, 0);
            if((prefs.getBoolean(OutputGPXTraceFragment.KEY_SYNCDROPBOX, false))
                    && (prefs.getBoolean(OutputGPXTraceFragment.KEY_ENABLE, false))
                    && (mGpxTrace != null))
                {
                    String szFilename = prefs.getString(OutputGPXTraceFragment.KEY_FILENAME, "");
                    if (szFilename.length()>0)
                    {
                        String szPath = MainActivity.getFileStorageDirectory() + File.separator + szFilename;
                        alDropboxed.add(new dpFile(szPath,insertDateTimeInDropboxFilename(szFilename)));
                    }

                }

             for(int i=0;i<alDropboxed.size();i++)
             {
                 String szCurrentPath = alDropboxed.get(i).getLocalFilenameWithPath();

                            if (!szCurrentPath.contains("%Y") && !szCurrentPath.contains("%m")
                                    && !szCurrentPath.contains("%d")
                                    && !szCurrentPath.contains("%h")
                                    && !szCurrentPath.contains("%M")
                                    && !szCurrentPath.contains("%S"))
                            {
                                DbxFileSystem dbxFs;
                                try {
                                    dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
                                    File f = new File(szCurrentPath);
                                    String remoteFileName = alDropboxed.get(i).getRemoteFilename();
                                    DbxFile remoteFile = dbxFs.create(new DbxPath(remoteFileName));
                                    remoteFile.writeFromExistingFile(f, false);
                                    remoteFile.close();
                                } catch (Unauthorized e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                } catch (InvalidPathException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                } catch (DbxException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }

                                Log.i(TAG, "Sync to dropbox: "+szCurrentPath);
                            }
             }
        }
    }
*/
    private void stop() {
        stopForeground(true);
        if (mCpuLock.isHeld()) mCpuLock.release();

        if (isServiceStarted()) {
            mRtkServer.stop();

            stopBluetoothPipes();
            stopUsb();
            // Tell the user we stopped.
            Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT)
            .show();
        }
    }

    @Override
    public void onDestroy() {
        stop();
    }

    @SuppressWarnings("deprecation")
    private Notification createForegroundNotification() {
        CharSequence text = getText(R.string.local_service_started);

        Notification notification = new Notification(R.drawable.ic_launcher,
                text, System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this
        // notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        notification.setLatestEventInfo(this,
                getText(R.string.local_service_label), text, contentIntent);

        return notification;
    }

    private class BluetoothCallbacks implements BluetoothToRtklib.Callbacks {

        private int mStreamId;
        private final Handler mHandler;

        public BluetoothCallbacks(int streamId) {
            mStreamId = streamId;
            mHandler = new Handler();
        }

        @Override
        public void onConnected() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(RtkNaviService.this, R.string.bluetooth_connected,
                            Toast.LENGTH_SHORT).show();
                }
            });

            new Thread() {
                @Override
                public void run() {
                    mRtkServer.sendStartupCommands(mStreamId);
                }
            }.start();
        }

        @Override
        public void onStopped() {
        }

        @Override
        public void onConnectionLost() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(RtkNaviService.this, R.string.bluetooth_connection_lost,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

    }

    private class UsbCallbacks implements UsbToRtklib.Callbacks {

        private int mStreamId;
        private final Handler mHandler;

        public UsbCallbacks(int streamId) {
            mStreamId = streamId;
            mHandler = new Handler();
        }

        @Override
        public void onConnected() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(RtkNaviService.this, R.string.usb_connected,
                            Toast.LENGTH_SHORT).show();
                }
            });

            new Thread() {
                @Override
                public void run() {
                    mRtkServer.sendStartupCommands(mStreamId);
                }
            }.run();

        }

        @Override
        public void onStopped() {
        }

        @Override
        public void onConnectionLost() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(RtkNaviService.this, R.string.usb_connection_lost,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

    }


    private void startBluetoothPipes() {
        final TransportSettings roverSettngs, baseSettings;

        RtkServerSettings settings = mRtkServer.getServerSettings();

        roverSettngs = settings.getInputRover().getTransportSettings();

        if (roverSettngs.getType() == StreamType.BLUETOOTH) {
            StreamBluetoothFragment.Value btSettings = (Value)roverSettngs;
            mBtRover = new BluetoothToRtklib(btSettings.getAddress().toUpperCase(), btSettings.getPath());
            mBtRover.setCallbacks(new BluetoothCallbacks(RtkServer.RECEIVER_ROVER));
            mBtRover.start();
        }else {
            mBtRover = null;
        }

        baseSettings = settings.getInputBase().getTransportSettings();
        if (baseSettings.getType() == StreamType.BLUETOOTH) {
            StreamBluetoothFragment.Value btSettings = (Value)baseSettings;
            mBtBase = new BluetoothToRtklib(btSettings.getAddress(), btSettings.getPath());
            mBtBase.setCallbacks(new BluetoothCallbacks(RtkServer.RECEIVER_BASE));
            mBtBase.start();
        }else {
            mBtBase = null;
        }
    }

    private void stopBluetoothPipes() {
        if (mBtRover != null) mBtRover.stop();
        if (mBtBase != null) mBtBase.stop();
        mBtRover = null;
        mBtBase = null;
    }

    private void startUsb() {
        RtkServerSettings settings = mRtkServer.getServerSettings();

        {
            final TransportSettings roverSettngs;
            roverSettngs = settings.getInputRover().getTransportSettings();
            if (roverSettngs.getType() == StreamType.USB) {
                StreamUsbFragment.Value usbSettings = (gpsplus.rtkgps.settings.StreamUsbFragment.Value)roverSettngs;
                mUsbReceiver = new UsbToRtklib(this, usbSettings.getPath());
                mUsbReceiver.setSerialLineConfiguration(usbSettings.getSerialLineConfiguration());
                mUsbReceiver.setCallbacks(new UsbCallbacks(RtkServer.RECEIVER_ROVER));
                mUsbReceiver.start();
                return;
            }
        }

        {
            final TransportSettings baseSettngs;
            baseSettngs = settings.getInputBase().getTransportSettings();
            if (baseSettngs.getType() == StreamType.USB) {
                StreamUsbFragment.Value usbSettings = (gpsplus.rtkgps.settings.StreamUsbFragment.Value)baseSettngs;
                mUsbReceiver = new UsbToRtklib(this, usbSettings.getPath());
                mUsbReceiver.setSerialLineConfiguration(usbSettings.getSerialLineConfiguration());
                mUsbReceiver.setCallbacks(new UsbCallbacks(RtkServer.RECEIVER_BASE));
                mUsbReceiver.start();
                return;
            }
        }
    }

    private void stopUsb() {
        if (mUsbReceiver != null) {
            mUsbReceiver.stop();
            mUsbReceiver = null;
        }
    }

    /* (non-Javadoc)
     * @see android.app.IntentService#onHandleIntent(android.content.Intent)
     */
    @Override
    protected void onHandleIntent(Intent arg0) {

        while (mBoolIsRunning){


            {
                try {
                    if (mBoolMockLocationsPref || mBoolGenerateGPXTrace)
                    {
                        RtkControlResult result = getRtkStatus(null);
                        Solution solution = result.getSolution();
                        Position3d positionECEF = solution.getPosition();

                        if (RtkCommon.norm(positionECEF.getValues()) > 0.0)
                        {
                            Position3d positionLatLon = RtkCommon.ecef2pos(positionECEF);

                            if (mBoolMockLocationsPref)
                            {
                                Location currentLocation = createLocation(Math.toDegrees(positionLatLon.getLat()), Math.toDegrees(positionLatLon.getLon()),positionLatLon.getHeight(), 1f);
                                if (mBoolLocationServiceIsConnected || true) // TO be corrected for Google maps API
                                    {
                                     // provide the new location
                                     //   Log.i(RTK_GPS_MOCK_LOCATION_SERVICE,"Mock location is "+currentLocation.getLatitude()+" "+currentLocation.getLongitude());
                                        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                                        locationManager.setTestProviderLocation(GPS_PROVIDER, currentLocation);
                                    }
                            }
                            if (mBoolGenerateGPXTrace)
                            {
                                if (mGpxTrace  == null)
                                {
                                    mGpxTrace = new GPXTrace();
                                }
                               mGpxTrace.addPoint(Math.toDegrees(positionLatLon.getLat()),
                                                   Math.toDegrees(positionLatLon.getLon()),
                                                   positionLatLon.getHeight(),
                                                   rtkCommon.getAltitudeCorrection(positionLatLon.getLat(), positionLatLon.getLon()),
                                                   solution.getTime());
                            }
                        }
                    }
                    Thread.sleep(mLProcessingCycle*1000);

                } catch (InterruptedException e) {
                    mBoolIsRunning = false;
                    Log.i(RTK_GPS_MOCK_LOCATION_SERVICE,"Mock location service was interrupted");
                }
            }
          }
    }

    @SuppressLint("NewApi")
    public Location createLocation(double lat, double lng, double alt, float accuracy) {
        // Create a new Location
        Location newLocation = new Location(GPS_PROVIDER);
        newLocation.setLatitude(lat);
        newLocation.setLongitude(lng);
        newLocation.setAccuracy(accuracy);
        newLocation.setAltitude(alt);
        newLocation.setTime(System.currentTimeMillis());


         try{

            Method m = newLocation.getClass().getMethod("setElapsedRealtimeNanos", long.class);
            if (m != null){
                newLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtime());
            }
        }
        catch(Exception e)
        {
            //nothing to do unsupported before API17
        }
        newLocation.setSpeed(0f);

        if (mLocationPrec == null){
            newLocation.setBearing(0f);
            newLocation.setSpeed(0f);
            mLocationPrec = newLocation;
        }else
        {
            float fBearing = mLocationPrec.bearingTo(newLocation)+180;
            if (fBearing>360)
            {
                fBearing -= 360;
            }
            float fSpeed = (mLocationPrec.distanceTo(newLocation))/((newLocation.getTime()-mLocationPrec.getTime())/1000);
            newLocation.setBearing(fBearing);
            newLocation.setSpeed(fSpeed);
            mLocationPrec = newLocation;
        }

        return newLocation;
    }
    @Override
    public void onLocationChanged(Location arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onProviderDisabled(String arg0) {
        mBoolLocationServiceIsConnected = false;
        Log.i(RTK_GPS_MOCK_LOCATION_SERVICE,"Mock location provide "+GPS_PROVIDER+" was disabled");

    }

    @Override
    public void onProviderEnabled(String arg0) {
        mBoolLocationServiceIsConnected = true;
        Log.i(RTK_GPS_MOCK_LOCATION_SERVICE,"Mock location provide "+GPS_PROVIDER+" is enabled");

    }

    @Override
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        // TODO Auto-generated method stub

    }


}
