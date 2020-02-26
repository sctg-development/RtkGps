package gpsplus.rtkgps.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;

import gpsplus.rtklib.ProcessingOptions;
import gpsplus.rtklib.RtkServerSettings;
import gpsplus.rtklib.RtkServerSettings.InputStream;
import gpsplus.rtklib.RtkServerSettings.LogStream;
import gpsplus.rtklib.RtkServerSettings.OutputStream;
import gpsplus.rtklib.RtkServerSettings.TransportSettings;
import gpsplus.rtklib.SolutionOptions;
import gpsplus.rtklib.constants.SolutionFormat;
import gpsplus.rtklib.constants.StreamFormat;
import gpsplus.rtklib.constants.StreamType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SettingsHelper {

    static final String NTRIP_REGEX = "^ntrip://(?:([^:]+)(?::([^@]+))?@)?([^:]+)(?::([0-9]+))?/(.+)$";

    static final int USER = 1;
    static final int PASSWORD = 2;
    static final int HOST = 3;
    static final int PORT = 4;
    static final int MOUNTPOINT = 5;

    static class StreamDefaultsBase {

        protected boolean enable;
        protected StreamType type;
        protected StreamFileClientFragment.Value fileClientDefaults;
        protected StreamNtripClientFragment.Value ntripClientDefaults;
        protected StreamNtripServerFragment.Value ntripServerDefaults;
        protected StreamTcpClientFragment.Value tcpClientDefaults;
        protected StreamBluetoothFragment.Value bluetoothDefaults;
        protected StreamUsbFragment.Value usbDefaults;

        public StreamDefaultsBase() {
            enable = false;
            type = StreamType.NTRIPCLI;
            fileClientDefaults = new StreamFileClientFragment.Value();
            ntripClientDefaults = new StreamNtripClientFragment.Value();
            ntripServerDefaults = new StreamNtripServerFragment.Value();
            tcpClientDefaults = new StreamTcpClientFragment.Value();
            bluetoothDefaults = new StreamBluetoothFragment.Value();
            usbDefaults = new StreamUsbFragment.Value();
        }

        public StreamDefaultsBase setEnabled(boolean enabled) {
            enable = enabled;
            return this;
        }

        public StreamDefaultsBase setType(StreamType type) {
            this.type = type;
            return this;
        }

        public StreamDefaultsBase setFileClientDefaults(StreamFileClientFragment.Value defaults) {
            this.fileClientDefaults = defaults;
            return this;
        }

        public StreamDefaultsBase setNtripClientDefaults(StreamNtripClientFragment.Value defaults) {
            this.ntripClientDefaults = defaults;
            return this;
        }


        public StreamDefaultsBase setNtripServerDefaults(StreamNtripServerFragment.Value defaults) {
            this.ntripServerDefaults = defaults;
            return this;
        }

        public StreamDefaultsBase setTcpClientDefaults(StreamTcpClientFragment.Value defaults) {
            this.tcpClientDefaults = defaults;
            return this;
        }
    }

    static class InputStreamDefaults extends StreamDefaultsBase {

        private StreamFormat format;
        private String receiverOption;
        protected StationPositionActivity.Value positionDefaults;

        public InputStreamDefaults() {
            super();
            format = StreamFormat.RTCM3;
            receiverOption = "";
            positionDefaults = new StationPositionActivity.Value();
        }

        public InputStreamDefaults setFormat(StreamFormat format) {
            this.format = format;
            return this;
        }

        public StreamDefaultsBase setPositionDefaults(StationPositionActivity.Value defaults) {
            this.positionDefaults = defaults;
            return this;
        }
    }

    static class OutputStreamDefaults extends StreamDefaultsBase {

        private SolutionFormat format;

        public OutputStreamDefaults() {
            super();
            type = StreamType.FILE;
            format = SolutionFormat.LLH;
        }

        public OutputStreamDefaults setFormat(SolutionFormat format) {
            this.format = format;
            return this;
        }
    }

    static class LogStreamDefaults extends StreamDefaultsBase {

        public LogStreamDefaults() {
            super();
            type = StreamType.FILE;
        }
    }


    public static void setDefaultValues(Context ctx, boolean force) {

        ProcessingOptions1Fragment.setDefaultValues(ctx, force);
        InputRoverFragment.setDefaultValues(ctx, force);
        InputBaseFragment.setDefaultValues(ctx, force);
        InputCorrectionFragment.setDefaultValues(ctx, force);
        OutputSolution1Fragment.setDefaultValues(ctx, force);
        OutputSolution2Fragment.setDefaultValues(ctx, force);
        LogRoverFragment.setDefaultValues(ctx, force);
        LogBaseFragment.setDefaultValues(ctx, force);
        LogCorrectionFragment.setDefaultValues(ctx, force);
    }


    public static RtkServerSettings loadSettings(Context ctx) {
        final RtkServerSettings settings;
        ProcessingOptions procOpts;
        SolutionOptions solOptsBase;

        settings = new RtkServerSettings();

        procOpts = ProcessingOptions1Fragment.readPrefs(ctx);
        settings.setProcessingOptions(procOpts);

        solOptsBase = SolutionOutputSettingsFragment.readPrefs(ctx);

        settings
        .setInputRover(InputRoverFragment.readPrefs(ctx))
        .setInputBase(InputBaseFragment.readPrefs(ctx))
        .setInputCorrection(InputCorrectionFragment.readPrefs(ctx))
        .setOutputSolution1(OutputSolution1Fragment.readPrefs(ctx, solOptsBase))
        .setOutputSolution2(OutputSolution2Fragment.readPrefs(ctx, solOptsBase))
        .setLogRover(LogRoverFragment.readPrefs(ctx))
        .setLogBase(LogBaseFragment.readPrefs(ctx))
        .setLogCorrection(LogCorrectionFragment.readPrefs(ctx))
        ;



        // TODO: send NMEA to base setting

        return settings;
    }

    static void setInputStreamDefaultValues(Context ctx,
            String sharedPrefsName,
            boolean force,
            InputStreamDefaults defaults
            ) {
        final SharedPreferences prefs;

        prefs = ctx.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);

        final boolean needUpdate = force || !prefs.contains(InputRoverFragment.KEY_ENABLE);

        if (needUpdate) {
            SharedPreferences.Editor e = prefs.edit();
            e.putBoolean(InputRoverFragment.KEY_ENABLE, defaults.enable)
            .putString(InputRoverFragment.KEY_TYPE, defaults.type.name())
            .putString(InputRoverFragment.KEY_FORMAT, defaults.format.name())
            .putString(InputRoverFragment.KEY_RECEIVER_OPTION, defaults.receiverOption)
            .commit();

            StartupShutdownSettingsActivity.setDefaultValue(ctx, sharedPrefsName);
            StationPositionActivity.setDefaultValue(ctx, sharedPrefsName, defaults.positionDefaults);
            StreamFileClientFragment.setDefaultValue(ctx, sharedPrefsName, defaults.fileClientDefaults);
            StreamNtripClientFragment.setDefaultValue(ctx, sharedPrefsName, defaults.ntripClientDefaults);
            StreamTcpClientFragment.setDefaultValue(ctx, sharedPrefsName, defaults.tcpClientDefaults);
            StreamBluetoothFragment.setDefaultValue(ctx, sharedPrefsName, defaults.bluetoothDefaults);
            StreamUsbFragment.setDefaultValue(ctx, sharedPrefsName, defaults.usbDefaults);
        }
    }

    static void setOutputStreamDefaultValues(Context ctx, String sharedPrefsName, boolean force,
            OutputStreamDefaults defaults) {
        final SharedPreferences prefs;

        prefs = ctx.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);

        final boolean needUpdate = force || !prefs.contains(OutputSolution1Fragment.KEY_ENABLE);

        if (needUpdate) {
            SharedPreferences.Editor e = prefs.edit();
            e.putBoolean(OutputSolution1Fragment.KEY_ENABLE, defaults.enable)
            .putString(OutputSolution1Fragment.KEY_TYPE, defaults.type.name())
            .putString(OutputSolution1Fragment.KEY_FORMAT, defaults.format.name())
            ;
            e.commit();
            StreamFileClientFragment.setDefaultValue(ctx, sharedPrefsName, defaults.fileClientDefaults);
            StreamNtripClientFragment.setDefaultValue(ctx, sharedPrefsName, defaults.ntripClientDefaults);
            StreamTcpClientFragment.setDefaultValue(ctx, sharedPrefsName,  defaults.tcpClientDefaults);
        }
    }

    static void setLogStreamDefaultValues(Context ctx, String sharedPrefsName, boolean force, LogStreamDefaults defaults) {
        final SharedPreferences prefs;

        prefs = ctx.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);

        final boolean needUpdate = force || !prefs.contains(LogRoverFragment.KEY_ENABLE);

        if (needUpdate) {
            SharedPreferences.Editor e = prefs.edit();
            e.putBoolean(LogRoverFragment.KEY_ENABLE, defaults.enable)
            .putString(LogRoverFragment.KEY_TYPE, defaults.type.name())
            ;
            e.commit();
            StreamFileClientFragment.setDefaultValue(ctx, sharedPrefsName, defaults.fileClientDefaults);
            StreamNtripServerFragment.setDefaultValue(ctx, sharedPrefsName, defaults.ntripServerDefaults);
            StreamTcpClientFragment.setDefaultValue(ctx, sharedPrefsName, defaults.tcpClientDefaults);
        }

    }

    @Nonnull
    static InputStream readInputStreamPrefs(Context ctx, String sharedPrefsName) {
        final SharedPreferences prefs;
        final InputStream stream;
        StreamType type;

        stream = new InputStream();
        prefs = ctx.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);

        if (!prefs.contains(InputRoverFragment.KEY_TYPE)) throw new IllegalStateException();

        if (!prefs.getBoolean(InputRoverFragment.KEY_ENABLE, false)) {
            stream.setTransportSettings(RtkServerSettings.TRANSPORT_DUMMY);
            return stream;
        }

        type = StreamType.valueOf(prefs.getString(InputRoverFragment.KEY_TYPE, null));
        stream
        .setFormat(StreamFormat.valueOf(prefs.getString(InputRoverFragment.KEY_FORMAT, StreamFormat.RTCM3.name())))
        .setCommandsAtStartup(
                prefs.getBoolean(StartupShutdownSettingsActivity.SHARED_PREFS_KEY_SEND_COMMANDS_AT_STARTUP, false),
                prefs.getString(StartupShutdownSettingsActivity.SHARED_PREFS_KEY_COMMANDS_AT_STARTUP, ""))
        .setCommandsAtShutdown(
                prefs.getBoolean(StartupShutdownSettingsActivity.SHARED_PREFS_KEY_SEND_COMMANDS_AT_SHUTDOWN, false),
                prefs.getString(StartupShutdownSettingsActivity.SHARED_PREFS_KEY_COMMANDS_AT_SHUTDOWN, ""))
        .setReceiverOption(prefs.getString(InputRoverFragment.KEY_RECEIVER_OPTION, ""))
        ;

        stream.setTransportSettings(readTransportSettings(ctx, type, prefs, sharedPrefsName));

        final StationPositionActivity.Value posSettings = StationPositionActivity.readSettings(prefs);
        stream.setStationPos(posSettings.getType(), posSettings.getPosition());

        return stream;
    }

    public static OutputStream readOutputStreamPrefs(Context ctx,
            String sharedPrefsName, SolutionOptions base) {
        final SharedPreferences prefs;
        final OutputStream stream;
        StreamType type;

        stream = new OutputStream();
        stream.setSolutionOptions(base);

        prefs = ctx.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);

        if (!prefs.contains(OutputSolution1Fragment.KEY_TYPE)) throw new IllegalStateException();

        if (!prefs.getBoolean(OutputSolution1Fragment.KEY_ENABLE, false)) {
            stream.setTransportSettings(RtkServerSettings.TRANSPORT_DUMMY);
            return stream;
        }

        type = StreamType.valueOf(prefs.getString(OutputSolution1Fragment.KEY_TYPE, null));
        stream
        .setSolutionFormat(SolutionFormat.valueOf(prefs.getString(OutputSolution1Fragment.KEY_FORMAT,SolutionFormat.NMEA.name())))
        .setTransportSettings(readTransportSettings(ctx, type, prefs, sharedPrefsName))
        ;

        return stream;
    }

    public static LogStream readLogStreamPrefs(Context ctx, String sharedPrefsName) {
        final SharedPreferences prefs;
        final LogStream stream;
        StreamType type;

        stream = new LogStream();

        prefs = ctx.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);

        if (!prefs.contains(LogRoverFragment.KEY_TYPE)) throw new IllegalStateException();

        if (!prefs.getBoolean(LogRoverFragment.KEY_ENABLE, false)) {
            stream.setTransportSettings(RtkServerSettings.TRANSPORT_DUMMY);
            return stream;
        }

        type = StreamType.valueOf(prefs.getString(LogRoverFragment.KEY_TYPE, null));
        stream.setTransportSettings(readTransportSettings(ctx, type, prefs, sharedPrefsName));

        return stream;
    }

    @Nonnull
    static TransportSettings readTransportSettings(Context context, StreamType type, SharedPreferences prefs, String stream) {
        TransportSettings settings;

        switch(type) {
        case FILE:
            settings = StreamFileClientFragment.readSettings(prefs);
            break;
        case NTRIPCLI:
            settings = StreamNtripClientFragment.readSettings(prefs);
            break;
        case NTRIPSVR:
            settings = StreamNtripServerFragment.readSettings(prefs);
            break;
        case TCPCLI:
            settings = StreamTcpClientFragment.readSettings(prefs);
            break;
        case UDPCLI:
            settings = StreamUdpClientFragment.readSettings(prefs);
            break;
        case MOBILEMAPPER:
            settings = StreamMobileMapperFragment.readSettings(context, prefs, stream);
            break;
        case BLUETOOTH:
            settings = StreamBluetoothFragment.readSettings(context, prefs, stream);
            break;
        case USB:
            settings = StreamUsbFragment.readSettings(context, prefs, stream);
            break;
        case NONE:
            settings = RtkServerSettings.TRANSPORT_DUMMY;
            break;
        default:
            throw new IllegalArgumentException();
        }
        return settings;
    }

    @Nonnull
    static String readInputStreamSumary(Resources resources, SharedPreferences prefs) {
        StreamType type;
        type = StreamType.valueOf(prefs.getString(InputRoverFragment.KEY_TYPE, StreamType.NONE.name()));
        return readStreamSummary(resources,type, prefs);
    }

    @Nonnull
    static String readOutputStreamSumary(Resources resources, SharedPreferences prefs) {
        StreamType type;
        type = StreamType.valueOf(prefs.getString(OutputSolution1Fragment.KEY_TYPE, StreamType.NONE.name()));
        return readStreamSummary(resources, type, prefs);
    }

    @Nonnull
    static String readLogStreamSumary(Resources resources, SharedPreferences prefs) {
        StreamType type;
        type = StreamType.valueOf(prefs.getString(LogRoverFragment.KEY_TYPE, StreamType.NONE.name()));
        return readStreamSummary(resources, type, prefs);
    }

    @Nonnull
    static String readStreamSummary(Resources resources, StreamType type, SharedPreferences prefs) {
        String summary;
        switch(type) {
        case FILE:
            summary = StreamFileClientFragment.readSummary(prefs);
            break;
        case NTRIPCLI:
            summary = StreamNtripClientFragment.readSummary(prefs);
            break;
        case NTRIPSVR:
            summary = StreamNtripServerFragment.readSummary(prefs);
            break;
        case TCPCLI:
            summary = StreamTcpClientFragment.readSummary(prefs);
            break;
        case UDPCLI:
            summary = StreamUdpClientFragment.readSummary(prefs);
            break;
        case BLUETOOTH:
            summary = StreamBluetoothFragment.readSummary(resources, prefs);
            break;
        case USB:
            summary = StreamUsbFragment.readSummary(resources, prefs);
            break;
        case MOBILEMAPPER:
            summary = StreamMobileMapperFragment.readSummary(prefs);
            break;
        case NONE:
            summary="";
            break;
        default:
            throw new IllegalArgumentException();
        }
        return summary;
    }

    /**
    *
    * @return [user[:passwd]@]addr[:port][/mntpnt[:str]])
    */
   @Nonnull
   static String encodeNtripTcpPath(
           @Nullable String user,
           @Nullable String passwd,
           @Nullable String host,
           @Nullable String port,
           @Nullable String mountpoint,
           @Nullable String str) {
       StringBuilder path;
       path = new StringBuilder();

       final boolean emptyUser, emptyPasswd;

       emptyUser = TextUtils.isEmpty(user);
       emptyPasswd = TextUtils.isEmpty(passwd);

       if (!emptyUser) {
          path.append(user.replaceAll(":", "")); //All characters except semicolon
       }

       if (!emptyPasswd) {
           if (!emptyUser) path.append(':');
           path.append(passwd.replaceAll(":", "")); //All characters except semicolon);
       }

       if (!emptyUser || !emptyPasswd) {
           path.append('@');
       }

       if (TextUtils.isEmpty(host)) host = "localhost";

       path.append(host);
       if (!TextUtils.isEmpty(port)) {
           path.append(':').append(port);
       }

       path.append('/');

       if (!TextUtils.isEmpty(mountpoint)) path.append(mountpoint);

       if (!TextUtils.isEmpty(str)) {
           path.append(':').append(str);
       }

       return path.toString();
   }

   static StreamNtripClientFragment.Value decodeNtripTcpPath(String path) {
       StreamNtripClientFragment.Value value = new StreamNtripClientFragment.Value();
       Pattern pattern = Pattern.compile(NTRIP_REGEX);
       Matcher matcher = pattern.matcher(path);
       if (!matcher.find()) {
          return null;
       }
       value
          .setUser(fixEmpty(matcher.group(USER)))
          .setPassword(fixEmpty(matcher.group(PASSWORD)))
          .setHost(matcher.group(HOST))
          .setPort(
             matcher.group(PORT) == null
             ? StreamNtripClientFragment.Value.DEFAULT_PORT
             : Integer.parseInt(matcher.group(PORT))
          )
          .setMountpoint(matcher.group(MOUNTPOINT));
       return value;
   }

   static String fixEmpty(String value) {
      return value == null ? "" : value;
   }

}
