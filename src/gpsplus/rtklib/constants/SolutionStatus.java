package gpsplus.rtklib.constants;

import gpsplus.rtkgps.R;

/**
 * Solution status SOLQ_XXX:  {@link #NONE},
 * {@link #FIX}, {@link #FLOAT}, {@link #SBAS},
 * {@link #DGPS}, {@link #SINGLE}, {@link #PPP},
 * {@link #DR}
 */
public enum SolutionStatus implements IHasRtklibId {

    NONE(0, R.string.solq_none, R.drawable.solution_none),
    FIX(1, R.string.solq_fix, R.drawable.solution_fix),
    FLOAT(2, R.string.solq_float, R.drawable.solution_float),
    SBAS(3, R.string.solq_sbas, R.drawable.solution_single),
    DGPS(4, R.string.solq_dgps, R.drawable.solution_single),
    SINGLE(5, R.string.solq_single, R.drawable.solution_single),
    PPP(6, R.string.solq_ppp, R.drawable.solution_fix),
    DR(7, R.string.solq_dr, R.drawable.solution_none),
    INTERNAL(8, R.string.solq_internal, R.drawable.solution_single)
    ;

    private final int mRtklibId;
    private final int mNameResId;
    private final int mIconResId;

    private SolutionStatus(int solqId, int nameResId, int iconResId) {
        mRtklibId = solqId;
        mNameResId = nameResId;
        mIconResId = iconResId;
    }

    public static SolutionStatus valueOf(int solqId) {
        for (SolutionStatus v: SolutionStatus.values()) {
            if (v.mRtklibId == solqId) {
                return v;
            }
        }
        throw new IllegalArgumentException();
    }

    @Override
    public int getRtklibId() {
        return mRtklibId;
    }

    @Override
    public int getNameResId() {
        return mNameResId;
    }

    public int getIconResId() {
        return mIconResId;
    }
};
