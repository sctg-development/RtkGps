package gpsplus.rtklib.constants;

import gpsplus.rtkgps.R;

/**
 * Positioning mode PMODE_XXX
 *
 */
public enum PositioningMode implements IHasRtklibId {

    /** positioning mode: single */
    SINGLE(0, R.string.pmode_single),
    /** positioning mode: DGPS/DGNSS */
    DGPS(1, R.string.pmode_dgps),
    /** positioning mode: kinematic */
    KINEMA(2, R.string.pmode_kinema),
    /** positioning mode: static */
    STATIC(3, R.string.pmode_static),
    /** positioning mode: static start **/
    STATIC_START(4, R.string.pmode_statstart),
    /** positioning mode: moving-base */
    MOVEB(5, R.string.pmode_moveb),
    /** positioning mode: fixed */
    FIXED(6, R.string.pmode_fixed),
    /** positioning mode: PPP-kinemaric */
    PPP_KINEMA(7, R.string.pmode_ppp_kinema),
    /** positioning mode: PPP-static */
    PPP_STATIC(8, R.string.pmode_ppp_static),
    /** positioning mode: PPP-fixed */
    PPP_FIXED(9, R.string.pmode_ppp_fixed)
    ;

    private final int mRtklibId;
    private final int mNameResId;

    private PositioningMode(int rtklibId, int nameResId) {
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

    public static PositioningMode valueOf(int rtklibId) {
        for (PositioningMode v: values()) {
            if (v.mRtklibId == rtklibId) return v;
        }
        throw new IllegalArgumentException();
    }

    public boolean isRelative() {
        return DGPS.equals(this)
                || KINEMA.equals(this)
                || STATIC.equals(this)
                || STATIC_START.equals(this)
                || MOVEB.equals(this)
                || FIXED.equals(this);
    }

    public boolean isPpp() {
        return PPP_KINEMA.equals(this)
                || PPP_STATIC.equals(this)
                || PPP_FIXED.equals(this);
    }

    public boolean isRtk() {
        return KINEMA.equals(this)
                || STATIC.equals(this)
                || MOVEB.equals(this)
                || FIXED.equals(this);
    }

    public boolean isMoveB() {
        return MOVEB.equals(this)
                || MOVEB.equals(this);
    }

    public boolean isFixed() {
        return FIXED.equals(this)
                || PPP_FIXED.equals(this);
    }
}
