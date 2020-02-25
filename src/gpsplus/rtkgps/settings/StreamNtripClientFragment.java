package gpsplus.rtkgps.settings;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.util.Log;

import gpsplus.rtkgps.BuildConfig;
import gpsplus.rtkgps.R;
import gpsplus.rtklib.RtkServerSettings.TransportSettings;
import gpsplus.rtklib.constants.StreamType;

import javax.annotation.Nonnull;


public class StreamNtripClientFragment extends PreferenceFragment {

    private static final boolean DBG = BuildConfig.DEBUG & true;

    private static final String KEY_HOST = "stream_ntrip_client_host";
    private static final String KEY_PORT = "stream_ntrip_client_port";
    private static final String KEY_MOUNTPOINT = "stream_ntrip_client_mountpoint";
    private static final String KEY_USER = "stream_ntrip_client_user";
    private static final String KEY_PASSWORD = "stream_ntrip_client_password";
    private static final String KEY_PATH = "stream_ntrip_path";

    private final PreferenceChangeListener mPreferenceChangeListener;

    private String mSharedPrefsName;

    public static final class Value implements TransportSettings, Cloneable {
        private @Nonnull String host;
        private int port;
        private @Nonnull String mountpoint;
        private @Nonnull String user;
        private @Nonnull String password;

        public static final String DEFAULT_HOST = "rt.igs-ip.net";
        public static final int DEFAULT_PORT = 2101;
        public static final String DEFAULT_MOUNTPOUNT = "BRST0";
        public static final String DEFAULT_USER = "request-user";
        public static final String DEFAULT_PASSWORD = "password";

        public Value() {
            host = DEFAULT_HOST;
            port = DEFAULT_PORT;
            mountpoint = DEFAULT_MOUNTPOUNT;
            user = DEFAULT_USER;
            password = DEFAULT_PASSWORD;
        }

        public Value setHost(@Nonnull String host) {
            if (host == null) throw new NullPointerException();
            this.host = host;
            return this;
        }

        public Value setPort(int port) {
            if (port <= 0 || port > 65535) {port=DEFAULT_PORT;}
            this.port = port;
            return this;
        }

        public Value setMountpoint(@Nonnull String mountpoint) {
            if (mountpoint == null) throw new NullPointerException();
            this.mountpoint = mountpoint;
            return this;
        }

        public Value setUser(@Nonnull String user) {
            if (user == null) throw new NullPointerException();
            this.user = user;
            return this;
        }

        public Value setPassword(@Nonnull String password) {
            if (password == null) throw new NullPointerException();
            this.password = password;
            return this;
        }

        @Override
        public StreamType getType() {
            return StreamType.NTRIPCLI;
        }

        @Override
        public String getPath() {
            return SettingsHelper.encodeNtripTcpPath(user, password, host,
                    String.valueOf(port), mountpoint, null);
        }

        public String getSummary() {
            return SettingsHelper.encodeNtripTcpPath(user,
                    "".equals(password) ? null : "xxx",
                    host,
                    String.valueOf(port),
                    mountpoint,
                    null);
        }

        @Override
        protected Value clone() {
            try {
                return (Value) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Value copy() {
            return clone();
        }

    }

    public StreamNtripClientFragment() {
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
        addPreferencesFromResource(R.xml.stream_ntrip_client_settings);
    }

    public static void setDefaultValue(Context ctx, String sharedPrefsName, Value value) {
        final SharedPreferences prefs;
        prefs = ctx.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);

        prefs.edit()
            .putString(KEY_HOST, value.host)
            .putString(KEY_PORT, String.valueOf(value.port))
            .putString(KEY_MOUNTPOINT, value.mountpoint)
            .putString(KEY_USER, value.user)
            .putString(KEY_PASSWORD, value.password)
            .putString(KEY_PATH, "")
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

        etp = (EditTextPreference) findPreference(KEY_HOST);
        etp.setSummary(etp.getText());

        etp = (EditTextPreference) findPreference(KEY_PORT);
        etp.setSummary(etp.getText());

        etp = (EditTextPreference) findPreference(KEY_MOUNTPOINT);
        etp.setSummary(etp.getText());

        etp = (EditTextPreference) findPreference(KEY_USER);
        etp.setSummary(etp.getText());

        etp = (EditTextPreference) findPreference(KEY_PASSWORD);
        etp.setSummary(etp.getText());

        etp = (EditTextPreference) findPreference(KEY_PATH);
        etp.setSummary(etp.getText());
    }

    private class PreferenceChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            if (key.equals(KEY_PATH)) {
                String path = ((EditTextPreference) findPreference(KEY_PATH)).getText();
                Value value = SettingsHelper.decodeNtripTcpPath(path);
                if (value != null) {
                    ((EditTextPreference) findPreference(KEY_HOST)).setText(value.host);
                    ((EditTextPreference) findPreference(KEY_PORT)).setText(String.valueOf(value.port));
                    ((EditTextPreference) findPreference(KEY_MOUNTPOINT)).setText(value.mountpoint);
                    ((EditTextPreference) findPreference(KEY_USER)).setText(value.user);
                    ((EditTextPreference) findPreference(KEY_PASSWORD)).setText(value.password);
                } else {
                    ((EditTextPreference) findPreference(KEY_PATH)).setText(readSummary(sharedPreferences));
                }
            } else {
                ((EditTextPreference) findPreference(KEY_PATH)).setText(readSummary(sharedPreferences));
            }
            reloadSummaries();
        }
    };

    public static Value readSettings(SharedPreferences prefs) {
        return new Value()
            .setUser(prefs.getString(KEY_USER, ""))
            .setPassword(prefs.getString(KEY_PASSWORD, ""))
            .setHost(prefs.getString(KEY_HOST, ""))
            .setPort(Integer.valueOf(prefs.getString(KEY_PORT, "0")))
            .setMountpoint(prefs.getString(KEY_MOUNTPOINT, ""))
            ;
    }

    public static String readSummary(SharedPreferences prefs) {
        return "ntrip://" + readSettings(prefs).getPath();
    }

}
