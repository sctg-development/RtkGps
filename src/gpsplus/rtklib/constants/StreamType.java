package gpsplus.rtklib.constants;

import gpsplus.rtkgps.R;


/** Stream type STR_XXX */
public enum StreamType implements IHasRtklibId {

    /** stream type: none */
    NONE(0, R.string.str_none),

    /** stream type: serial */
    SERIAL(1, R.string.str_serial),

    /** stream type: file */
    FILE(2, R.string.str_file),

    /** stream type: TCP server */
    TCPSVR(3, R.string.str_tcpsvr),

    /** stream type: TCP client */
    TCPCLI(4, R.string.str_tcpcli),

    /** stream type: UDP */
    UDP(5, R.string.str_udp),

    /** stream type: NTRIP server */
    NTRIPSVR(6, R.string.str_ntripsvr),

    /** stream type: NTRIP client */
    NTRIPCLI(7, R.string.str_ntripcli),

    /** stream type: ftp */
    FTP(8, R.string.str_ftp),

    /** stream type: http */
    HTTP(9, R.string.str_http),

    /** stream type: unix server */
    UNIXSVR(15, R.string.str_unixsvr),

    // XXX
    BLUETOOTH(15, R.string.str_bluetooth),

    USB(15, R.string.str_usb)

    ;

    private final int mRtklibId;
    private final int mNameResId;

    private StreamType(int rtklibId, int nameResId) {
        mRtklibId = rtklibId;
        mNameResId = nameResId;
    }

    @Override
    public int getRtklibId() {
        return mRtklibId;
    }

    @Override
    public int getNameResId() {
        return mNameResId;
    }

    public static StreamType valueOf(int rtklibId) {
        for (StreamType v: values()) {
            if (v.mRtklibId == rtklibId) return v;
        }
        throw new IllegalArgumentException();
    }
}
