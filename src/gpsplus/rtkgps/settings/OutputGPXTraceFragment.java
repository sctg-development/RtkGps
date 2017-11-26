package gpsplus.rtkgps.settings;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;

//import com.dropbox.sync.android.DbxAccountManager;

import gpsplus.rtkgps.MainActivity;
import gpsplus.rtkgps.R;


public class OutputGPXTraceFragment extends PreferenceFragment {

    public static final String SHARED_PREFS_NAME = "OutputGPXTrace";
    @SuppressWarnings("unused")
    private static final String XML = "output_gpxtrace_settings.xml";
  //  public static final String KEY_SYNCDROPBOX = "syncdropbox";
    public static final String KEY_ENABLE = "enable";
    public static final String KEY_FILENAME = "gpxtrace_file_filename";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(SHARED_PREFS_NAME);
        addPreferencesFromResource(R.xml.output_gpxtrace_settings);
/*
     // Setup on change listener for checkbox - to track when user turns if off
        super.findPreference(KEY_SYNCDROPBOX)
        .setOnPreferenceChangeListener( new OnPreferenceChangeListener(){
                                        @Override
                                        public boolean onPreferenceChange(Preference pref, Object val) {
                                            Boolean checkBoxVal = (Boolean) val;
                                            if(checkBoxVal.booleanValue()==true) {
                                                // User just turned on checkbox - checks if dropbox is linked
                                                DbxAccountManager mDbxAcctMgr;
                                                mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(),
                                                                                            MainActivity.APP_KEY, MainActivity.APP_SECRET);
                                                if (!mDbxAcctMgr.hasLinkedAccount())
                                                {
                                                    mDbxAcctMgr.startLink(getActivity(), MainActivity.REQUEST_LINK_TO_DBX);
                                                }
                                            }
                                            return true; // Finally, let the checkbox value go through to update itself
                                        }
                                    }); // end of checkbox listener
 */
    }

    public Context getApplicationContext() {
        // return application context
        return this.getActivity().getApplicationContext();
    }

}
