package gpsplus.rtkgps;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceActivity;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.listener.multi.DialogOnAnyDeniedMultiplePermissionsListener;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import butterknife.BindString;
import butterknife.ButterKnife;
import butterknife.BindView;

// import com.dropbox.sync.android.DbxAccountManager;

import gpsplus.ntripcaster.NTRIPCaster;
import gpsplus.rtkgps.settings.NTRIPCasterSettingsFragment;
import gpsplus.rtkgps.settings.ProcessingOptions1Fragment;
import gpsplus.rtkgps.settings.SettingsActivity;
import gpsplus.rtkgps.settings.SettingsHelper;
import gpsplus.rtkgps.settings.SolutionOutputSettingsFragment;
import gpsplus.rtkgps.settings.StreamSettingsActivity;
import gpsplus.rtkgps.utils.ChangeLog;
import gpsplus.rtkgps.utils.GpsTime;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.annotation.Nonnull;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener{

    private static final boolean DBG = BuildConfig.DEBUG & true;
//    public static final int REQUEST_LINK_TO_DBX = 2654;
    static final String TAG = MainActivity.class.getSimpleName();

    /**
     * The serialization (saved instance state) Bundle key representing the
     * current dropdown position.
     */
    private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";
    public static final String APP_KEY = "6ffqsgh47v9y5dc";
    public static final String APP_SECRET = "hfmsbkv4ktyl60h";
    public static final String RTKGPS_CHILD_DIRECTORY = "RtkGps/";
//    private DbxAccountManager mDbxAcctMgr;

    RtkNaviService mRtkService;
    boolean mRtkServiceBound = false;
    private static DemoModeLocation mDemoModeLocation;
    private String mSessionCode;
    String m_PointName = "POINT";
    boolean m_bRet_pointName = false;

    @BindView(R.id.drawer_layout) DrawerLayout mDrawerLayout;
    @BindView(R.id.navigation_drawer) View mNavDrawer;

    @BindView(R.id.navdraw_server_switch) Switch mNavDrawerServerSwitch;
    @BindView(R.id.navdraw_ntripcaster_switch) Switch mNavDrawerCasterSwitch;

    @BindString(R.string.permissions_request_title) String permissionTitle;
    @BindString(R.string.permissions_request_message) String permissionMessage;

    private ActionBarDrawerToggle mDrawerToggle;

    private int mNavDraverSelectedItem;
    private static String mApplicationDirectory = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MultiplePermissionsListener dialogMultiplePermissionsListener =
                DialogOnAnyDeniedMultiplePermissionsListener.Builder
                        .withContext(this)
                        .withTitle(permissionTitle)
                        .withMessage(permissionMessage)
                        .withButtonText(android.R.string.ok)
                        .build();
        // New Permissions request for newer Android SDK
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.ACCESS_NETWORK_STATE,
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.INTERNET,
                        Manifest.permission.WAKE_LOCK,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ).withListener(dialogMultiplePermissionsListener)
                .check();

        PackageManager m = getPackageManager();
        String s = getPackageName();
        try {
            PackageInfo p = m.getPackageInfo(s, 0);
            MainActivity.mApplicationDirectory = p.applicationInfo.dataDir;
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Error Package name not found ", e);
        }

        // copy assets/data
        try {
            copyAssetsToApplicationDirectory();
            copyAssetsToWorkingDirectory();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

//        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), APP_KEY, APP_SECRET);

        mDemoModeLocation = new DemoModeLocation(this.getApplicationContext());

        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        toggleCasterSwitch();

        createDrawerToggle();

        if (savedInstanceState == null) {
            SettingsHelper.setDefaultValues(this, false);
            proxyIfUsbAttached(getIntent());
            selectDrawerItem(R.id.navdraw_item_status);
            mDrawerLayout.openDrawer(mNavDrawer);
        }

        mNavDrawerServerSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!buttonView.isPressed()) {
                    return;
                }
                mDrawerLayout.closeDrawer(mNavDrawer);
                if (isChecked) {
                    startRtkService();
                } else {
                    stopRtkService();
                }
                invalidateOptionsMenu();
            }
        });
        mNavDrawerCasterSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            private NTRIPCaster mCaster = null;

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDrawerLayout.closeDrawer(mNavDrawer);
                if (isChecked) {
                    if (mCaster == null)
                    {
                        mCaster = new NTRIPCaster(getFileStorageDirectory()+"/ntripcaster/conf");
                    }
                    mCaster.start(2101, "none");
                    //TEST
                }else {
                    if (getCasterBrutalEnding())
                    {
                        stopRtkService();
                        int ret = mCaster.stop(1);
                        android.os.Process.killProcess(android.os.Process.myPid()); //in case of not stopping
                    }else{
                        int ret = mCaster.stop(0);
                        Log.v(TAG, "NTRIPCaster.stop(0)="+ret);

                    }
                }
                invalidateOptionsMenu();
            }
        });

        ChangeLog cl = new ChangeLog(this);
        if (cl.firstRun())
            cl.getLogDialog().show();
    }

    private void toggleCasterSwitch() {
        SharedPreferences casterSolution = getSharedPreferences(NTRIPCasterSettingsFragment.SHARED_PREFS_NAME, 0);
        boolean bIsCasterEnabled = casterSolution.getBoolean(NTRIPCasterSettingsFragment.KEY_ENABLE_CASTER, false);
        mNavDrawerCasterSwitch.setEnabled(bIsCasterEnabled);
    }

    private boolean getCasterBrutalEnding() {
        SharedPreferences casterSolution = getSharedPreferences(NTRIPCasterSettingsFragment.SHARED_PREFS_NAME, 0);
        return casterSolution.getBoolean(NTRIPCasterSettingsFragment.KEY_BRUTAL_ENDING_CASTER, true);
    }

    public static DemoModeLocation getDemoModeLocation(){
        return mDemoModeLocation;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mRtkServiceBound) {
            final Intent intent = new Intent(this, RtkNaviService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        proxyIfUsbAttached(intent);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Unbind from the service
        if (mRtkServiceBound) {
            unbindService(mConnection);
            mRtkServiceBound = false;
            mRtkService = null;
        }

    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
            mNavDraverSelectedItem = savedInstanceState.getInt(STATE_SELECTED_NAVIGATION_ITEM);
            setNavDrawerItemChecked(mNavDraverSelectedItem);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mNavDraverSelectedItem != 0) {
            outState.putInt(STATE_SELECTED_NAVIGATION_ITEM, mNavDraverSelectedItem);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean serviceActive = mNavDrawerServerSwitch.isChecked();
        menu.findItem(R.id.menu_start_service).setVisible(!serviceActive);
        menu.findItem(R.id.menu_stop_service).setVisible(serviceActive);
        menu.findItem(R.id.menu_add_point).setVisible(serviceActive);
        menu.findItem(R.id.menu_tools).setVisible(true);
//        if (mDbxAcctMgr.hasLinkedAccount())
//        {
//            menu.findItem(R.id.menu_dropbox).setVisible(false);
//        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
        case R.id.menu_start_service:
            startRtkService();
            break;
        case R.id.menu_stop_service:
            stopRtkService();
            break;
        case R.id.menu_add_point:
            askToAddPointToCrw();
            break;
        case R.id.menu_tools:
            startActivity(new Intent(this, ToolsActivity.class));
            break;
//        case R.id.menu_dropbox:
//            mDbxAcctMgr.startLink(this, REQUEST_LINK_TO_DBX);
//            break;
        case R.id.menu_settings:
            mDrawerLayout.openDrawer(mNavDrawer);
            break;
        case R.id.menu_about:
            startActivity(new Intent(this, AboutActivity.class));
            break;
        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private boolean askForPointName()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this,R.style.PointNameAlertDialogStyle);
        builder.setTitle(R.string.point_name_input_title);


        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT );
        builder.setView(input);

        builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                 m_PointName = input.getText().toString();
                 m_bRet_pointName = true;
            }
        });
        builder.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                m_bRet_pointName = false;
                dialog.cancel();
            }
        });

        builder.show();
        return m_bRet_pointName;
    }
    private void askToAddPointToCrw()
    {
        if (askForPointName()) {
            final Intent intent = new Intent(RtkNaviService.ACTION_STORE_POINT);
            intent.setClass(this, RtkNaviService.class);
            intent.putExtra(RtkNaviService.EXTRA_POINT_NAME, m_PointName);
            startService(intent);
        }
    }

    private void copyAssetsDirToApplicationDirectory(String sourceDir, File destDir) throws FileNotFoundException, IOException
    {
        //copy assets/data to appdir/data
        java.io.InputStream stream = null;
        java.io.OutputStream output = null;

        for(String fileName : this.getAssets().list(sourceDir))
        {
            stream = this.getAssets().open(sourceDir+File.separator + fileName);
            String dest = destDir+ File.separator + sourceDir + File.separator + fileName;
            File fdest = new File(dest);
            if (fdest.exists()) continue;

            File fpdestDir = new File(fdest.getParent());
            if ( !fpdestDir.exists() ) fpdestDir.mkdirs();

            output = new BufferedOutputStream(new FileOutputStream(dest));

            byte data[] = new byte[1024];
            int count;

            while((count = stream.read(data)) != -1)
            {
                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            stream.close();

            stream = null;
            output = null;
        }
    }

    private void copyAssetsToApplicationDirectory() throws FileNotFoundException, IOException
    {
       copyAssetsDirToApplicationDirectory("data",this.getFilesDir());
       copyAssetsDirToApplicationDirectory("proj4",this.getFilesDir());
    }

    private void copyAssetsToWorkingDirectory() throws FileNotFoundException, IOException
    {
        copyAssetsDirToApplicationDirectory("ntripcaster",getFileStorageDirectory());
    }

    private void proxyIfUsbAttached(Intent intent) {

        if (intent == null) return;

        if (!UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) return;

        if (DBG) Log.v(TAG, "usb device attached");

        final Intent proxyIntent = new Intent(UsbToRtklib.ACTION_USB_DEVICE_ATTACHED);
        proxyIntent.putExtras(intent.getExtras());
        sendBroadcast(proxyIntent);
    }

    private void createDrawerToggle() {
        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                R.drawable.ic_drawer,
                R.string.drawer_open,
                R.string.drawer_close
                ) {
            @Override
            public void onDrawerClosed(View view) {
                //getActionBar().setTitle(mTitle);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                //getActionBar().setTitle(mDrawerTitle);
            }

        };
    }

    private void selectDrawerItem(int itemId) {
        switch (itemId) {
        case R.id.navdraw_item_status:
        case R.id.navdraw_item_map:
            setNavDrawerItemFragment(itemId);
            break;
        case R.id.navdraw_item_input_streams:
            showInputStreamSettings();
            break;
        case R.id.navdraw_item_output_streams:
            showOutputStreamSettings();
            break;
        case R.id.navdraw_item_log_streams:
            showLogStreamSettings();
            break;
        case R.id.navdraw_item_processing_options:
        case R.id.navdraw_item_solution_options:
        case R.id.navdraw_item_ntripcaster_options:
            showSettings(itemId);
            break;
        default:
            throw new IllegalStateException();
        }
    }

    private void setNavDrawerItemFragment(int itemId) {
        Fragment fragment;
        mDrawerLayout.closeDrawer(mNavDrawer);

        if (mNavDraverSelectedItem == itemId) {
            return;
        }

        switch (itemId) {
        case R.id.navdraw_item_status:
            fragment = new StatusFragment();
            break;
        case R.id.navdraw_item_map:
            fragment = new MapFragment();
            break;
        default:
            throw new IllegalArgumentException();
        }

        getFragmentManager()
        .beginTransaction()
        .replace(R.id.container, fragment)
        .commit();
        setNavDrawerItemChecked(itemId);
    }

    private void setNavDrawerItemChecked(int itemId) {
        final int[] items = new int[] {
            R.id.navdraw_item_status,
            R.id.navdraw_item_input_streams,
            R.id.navdraw_item_output_streams,
            R.id.navdraw_item_log_streams,
            R.id.navdraw_item_solution_options,
            R.id.navdraw_item_solution_options
        };

        for (int i: items) {
            mNavDrawer.findViewById(i).setActivated(itemId == i);
        }
        mNavDraverSelectedItem = itemId;
    }

    private void refreshServiceSwitchStatus() {
        boolean serviceActive = mRtkServiceBound && (mRtkService.isServiceStarted());
        mNavDrawerServerSwitch.setChecked(serviceActive);
    }

    private void startRtkService() {
        GpsTime gpsTime = new GpsTime();
        gpsTime.setTime(System.currentTimeMillis());
        mSessionCode =String.format("%s_%s",gpsTime.getStringGpsWeek(),gpsTime.getStringGpsTOW());
        final Intent rtkServiceIntent = new Intent(RtkNaviService.ACTION_START);
        rtkServiceIntent.putExtra(RtkNaviService.EXTRA_SESSION_CODE,mSessionCode);
        rtkServiceIntent.setClass(this, RtkNaviService.class);
        startService(rtkServiceIntent);
        mNavDrawerServerSwitch.setChecked(true);
    }

    public String getSessionCode() {
        return mSessionCode;
    }

    private void stopRtkService() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.PointNameAlertDialogStyle);
        builder.setTitle("Confirm stop");
        builder.setMessage("Do you really want to stop service?");
        builder.setIcon(R.drawable.ic_launcher);
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                stopRtkServiceConfirmed();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                if (!mNavDrawerServerSwitch.isChecked()) {
                    mNavDrawerServerSwitch.setChecked(true);
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void stopRtkServiceConfirmed() {
        final Intent intent = new Intent(RtkNaviService.ACTION_STOP);
        intent.setClass(this, RtkNaviService.class);
        startService(intent);
        mNavDrawerServerSwitch.setChecked(false);
    }

    public RtkNaviService getRtkService() {
        return mRtkService;
    }

    private void showSettings(int itemId) {
        final Intent intent = new Intent(this, SettingsActivity.class);
        switch (itemId) {
        case R.id.navdraw_item_processing_options:
            intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                    ProcessingOptions1Fragment.class.getName());
            break;
        case R.id.navdraw_item_solution_options:
            intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                    SolutionOutputSettingsFragment.class.getName());
            break;
        case R.id.navdraw_item_ntripcaster_options:
            intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                    NTRIPCasterSettingsFragment.class.getName());
            break;
        default:
            throw new IllegalStateException();
        }
        startActivity(intent);
    }

    private void showInputStreamSettings() {
        final Intent intent = new Intent(this, StreamSettingsActivity.class);
        intent.putExtra(StreamSettingsActivity.ARG_STEAM,
                StreamSettingsActivity.STREAM_INPUT_SETTINGS);
        startActivity(intent);
    }

    private void showOutputStreamSettings() {
        final Intent intent = new Intent(this, StreamSettingsActivity.class);
        intent.putExtra(StreamSettingsActivity.ARG_STEAM,
                StreamSettingsActivity.STREAM_OUTPUT_SETTINGS);
        startActivity(intent);
    }

    private void showLogStreamSettings() {
        final Intent intent = new Intent(this, StreamSettingsActivity.class);
        intent.putExtra(StreamSettingsActivity.ARG_STEAM,
                StreamSettingsActivity.STREAM_LOG_SETTINGS);
        startActivity(intent);
    }


    public void onNavDrawevItemClicked(View v) {
        selectDrawerItem(v.getId());
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get
            // LocalService instance
            RtkNaviService.RtkNaviServiceBinder binder = (RtkNaviService.RtkNaviServiceBinder) service;
            mRtkService = binder.getService();
            mRtkServiceBound = true;
            refreshServiceSwitchStatus();
            invalidateOptionsMenu();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mRtkServiceBound = false;
            mRtkService = null;
            refreshServiceSwitchStatus();
            invalidateOptionsMenu();
        }
    };

    @Nonnull
    public static File getFileStorageDirectory() {
        File externalLocation = new File(Environment.getExternalStorageDirectory(), RTKGPS_CHILD_DIRECTORY);
        if(!externalLocation.isDirectory()) {
           if (externalLocation.mkdirs()) {
               Log.v(TAG, "Local storage created on external card");
           }else{
               externalLocation = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),RTKGPS_CHILD_DIRECTORY);
               if(!externalLocation.isDirectory()) {
                   if (externalLocation.mkdirs()) {
                       Log.v(TAG, "Local storage created on public storage");
                   }else{
                       externalLocation = new File(Environment.getDownloadCacheDirectory(), RTKGPS_CHILD_DIRECTORY);
                       if (!externalLocation.isDirectory()){
                           if (externalLocation.mkdirs()){
                               Log.v(TAG, "Local storage created on cache directory");
                           }else{
                               externalLocation = new File(Environment.getDataDirectory(),RTKGPS_CHILD_DIRECTORY);
                               if(!externalLocation.isDirectory()) {
                                   if (externalLocation.mkdirs()) {
                                       Log.v(TAG, "Local storage created on data storage");
                                   }else{
                                       Log.e(TAG,"NO WAY TO CREATE FILE SOTRAGE?????");
                                   }
                               }
                           }
                       }
                   }
               }
           }
        }
        return externalLocation;
    }

    @Nonnull
    public static File getFileInStorageDirectory(String nameWithExtension) {
        return new File(Environment.getExternalStorageDirectory(), RTKGPS_CHILD_DIRECTORY+nameWithExtension);
    }
    public static String getAndCheckSessionDirectory(String code){
        String szSessionDirectory = MainActivity.getFileStorageDirectory() + File.separator + code;
        File fsessionDirectory = new File(szSessionDirectory);
        if (!fsessionDirectory.exists()){
            fsessionDirectory.mkdirs();
        }
        return szSessionDirectory;
    }

    public static File getFileInStorageSessionDirectory(String code, String nameWithExtension){
        String szSessionDirectory = MainActivity.getAndCheckSessionDirectory(code);
        return new File (szSessionDirectory+File.separator+nameWithExtension);
    }


    @Nonnull
    public static File getLocalSocketPath(Context ctx, String socketName) {
        return ctx.getFileStreamPath(socketName);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (requestCode == REQUEST_LINK_TO_DBX) {
//            if (resultCode == Activity.RESULT_OK) {
                // ... Start using Dropbox files.
//            } else {
                // ... Link failed or was cancelled by the user.
 //           }
 //       } else {
            super.onActivityResult(requestCode, resultCode, data);
 //       }
    }
    public static String getApplicationDirectory() {
        return MainActivity.mApplicationDirectory;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equalsIgnoreCase(NTRIPCasterSettingsFragment.KEY_ENABLE_CASTER))
        {
            toggleCasterSwitch();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences(NTRIPCasterSettingsFragment.SHARED_PREFS_NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSharedPreferences(NTRIPCasterSettingsFragment.SHARED_PREFS_NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
    }

}
