package gpsplus.rtkgps.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.util.Log;

//import com.dropbox.sync.android.DbxAccountManager;

import gpsplus.rtkgps.BuildConfig;
import gpsplus.rtkgps.MainActivity;
import gpsplus.rtkgps.R;
import gpsplus.rtklib.RtkServerSettings.TransportSettings;
import gpsplus.rtklib.constants.StreamType;

import java.io.File;

import javax.annotation.Nonnull;

public class StreamFileClientFragment extends PreferenceFragment {

    private static final boolean DBG = BuildConfig.DEBUG & true;

    private static final String KEY_FILENAME = "stream_file_filename";
//    public static final String KEY_SYNCDROPBOX = "syncdropbox";
//    public static final String KEY_ZIPBEFORESYNCING = "zipbeforesync";
    public static final String KEY_ENABLE = "enable";

    private final PreferenceChangeListener mPreferenceChangeListener;

    private String mSharedPrefsName;


    public static final class Value implements TransportSettings {
        private String filename;

        public static final String DEFAULT_FILENAME = "stream.log";

        public Value() {
            filename = DEFAULT_FILENAME;
        }

        public Value setFilename(@Nonnull String filename) {
            if (filename == null) throw new NullPointerException();
            this.filename = filename;
            return this;
        }

        @Override
        public StreamType getType() {
            return StreamType.FILE;
        }

        @Override
        public String getPath() {
            return (new File(MainActivity.getFileStorageDirectory(), filename)).getAbsolutePath();
        }

        @Override
        public Value copy() {
            return new Value().setFilename(filename);
        }
    }

    public StreamFileClientFragment() {
        super();
        mPreferenceChangeListener = new PreferenceChangeListener();
        mSharedPrefsName = StreamNtripClientFragment.class.getSimpleName();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle arguments;

        arguments = getArguments();
        if (arguments == null || !arguments.containsKey(StreamDialogActivity.ARG_SHARED_PREFS_NAME)) {
            throw new IllegalArgumentException("ARG_SHARED_PREFFS_NAME argument not defined");
        }

        mSharedPrefsName = arguments.getString(StreamDialogActivity.ARG_SHARED_PREFS_NAME);

        if (DBG) Log.v(mSharedPrefsName, "onCreate()");

        getPreferenceManager().setSharedPreferencesName(mSharedPrefsName);

        initPreferenceScreen();
    }

    protected void initPreferenceScreen() {
        if (DBG) Log.v(mSharedPrefsName, "initPreferenceScreen()");
        addPreferencesFromResource(R.xml.stream_file_settings);
    }

    public static void setDefaultValue(Context ctx, String sharedPrefsName, Value value) {
        final SharedPreferences prefs;
        prefs = ctx.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);
        prefs
            .edit()
            .putString(KEY_FILENAME, value.filename)
            .apply();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DBG) Log.v(mSharedPrefsName, "onResume()");
        reloadSummaries();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }

    @Override
    public void onPause() {
        if (DBG) Log.v(mSharedPrefsName, "onPause()");
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        super.onPause();
    }

    void reloadSummaries() {
        EditTextPreference etp;
        etp = (EditTextPreference) findPreference(KEY_FILENAME);
        etp.setSummary(etp.getText()+"\n"+getResources().getString(R.string.file_filename_summary));

    }

    private class PreferenceChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
//            if (key.equals(KEY_SYNCDROPBOX))
//            {
//                if (sharedPreferences.getBoolean(KEY_SYNCDROPBOX, false))
//                {
//                    DbxAccountManager mDbxAcctMgr;
//                    mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), MainActivity.APP_KEY, MainActivity.APP_SECRET);
//                    if (!mDbxAcctMgr.hasLinkedAccount())
//                    {
//                        mDbxAcctMgr.startLink(getActivity(), MainActivity.REQUEST_LINK_TO_DBX);
//                    }
//                }
//            }
            reloadSummaries();
        }
    };

    @Nonnull
    public static Value readSettings(SharedPreferences prefs) {
        final Value v = new Value();
        final String filename;

        filename = prefs.getString(KEY_FILENAME, null);
        if (filename == null)  throw new IllegalStateException("setDefaultValues() must be called");

        if (filename.length() != 0) v.setFilename(filename);
        return v;
    }

    public Context getApplicationContext() {
        // return application context
        return this.getActivity().getApplicationContext();
    }

    public static String readSummary(SharedPreferences prefs) {
        return "file://" + readSettings(prefs).filename;
    }


}
