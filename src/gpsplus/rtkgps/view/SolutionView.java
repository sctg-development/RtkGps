package gpsplus.rtkgps.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TextView;

import gpsplus.rtkgps.BuildConfig;
import gpsplus.rtkgps.DemoModeLocation;
import gpsplus.rtkgps.MainActivity;
import gpsplus.rtkgps.Proj4Converter;
import gpsplus.rtkgps.R;
import gpsplus.rtkgps.RtkNaviService;
import gpsplus.rtkgps.settings.SolutionOutputSettingsFragment;
import gpsplus.rtkgps.utils.UTM;
import gpsplus.rtklib.RtkCommon;
import gpsplus.rtklib.RtkCommon.Deg2Dms;
import gpsplus.rtklib.RtkCommon.Matrix3x3;
import gpsplus.rtklib.RtkCommon.Position3d;
import gpsplus.rtklib.RtkControlResult;
import gpsplus.rtklib.Solution;
import gpsplus.rtklib.constants.GeoidModel;
import gpsplus.rtklib.constants.SolutionStatus;

import org.proj4.CRSRegistry;
import org.proj4.ProjCoordinate;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public class SolutionView extends TableLayout {

    @SuppressWarnings("unused")
    private static final boolean DBG = BuildConfig.DEBUG & true;
    static final String TAG = SolutionView.class.getSimpleName();

    public enum Format {
        WGS84(0,
                R.string.solution_view_format_wgs84,
                R.array.solution_view_coordinates_wgs84,
                null),

        WGS84_FLOAT(1,
                R.string.solution_view_format_wgs84_float,
                R.array.solution_view_coordinates_wgs84,
                null),
        UTM(2,
                R.string.solution_view_format_utm,
                R.array.solution_view_coordinates_utm,
                null),

        PROJ4_LAMBERT93(3,
                R.string.solution_view_format_proj4_lambert93,
                R.array.solution_view_coordinates_proj4_lambert93,
                CRSRegistry.IGNF_LAMB93),

        PROJ4_LAMBERT93_CC43(4,
                R.string.solution_view_format_proj4_lambert93_cc43,
                R.array.solution_view_coordinates_proj4_lambert93,
                CRSRegistry.IGNF_RGF93CC43),
        PROJ4_LAMBERT93_CC44(5,
                R.string.solution_view_format_proj4_lambert93_cc44,
                R.array.solution_view_coordinates_proj4_lambert93,
                CRSRegistry.IGNF_RGF93CC44),
        PROJ4_LAMBERT93_CC45(6,
                R.string.solution_view_format_proj4_lambert93_cc45,
                R.array.solution_view_coordinates_proj4_lambert93,
                CRSRegistry.IGNF_RGF93CC45),
        PROJ4_LAMBERT93_CC46(7,
                R.string.solution_view_format_proj4_lambert93_cc46,
                R.array.solution_view_coordinates_proj4_lambert93,
                CRSRegistry.IGNF_RGF93CC46),
        PROJ4_LAMBERT93_CC47(8,
                R.string.solution_view_format_proj4_lambert93_cc47,
                R.array.solution_view_coordinates_proj4_lambert93,
                CRSRegistry.IGNF_RGF93CC47),
        PROJ4_LAMBERT93_CC48(9,
                R.string.solution_view_format_proj4_lambert93_cc48,
                R.array.solution_view_coordinates_proj4_lambert93,
                CRSRegistry.IGNF_RGF93CC48),
        PROJ4_LAMBERT93_CC49(10,
                R.string.solution_view_format_proj4_lambert93_cc49,
                R.array.solution_view_coordinates_proj4_lambert93,
                CRSRegistry.IGNF_RGF93CC49),
        PROJ4_LAMBERT93_CC50(11,
                R.string.solution_view_format_proj4_lambert93_cc50,
                R.array.solution_view_coordinates_proj4_lambert93,
                CRSRegistry.IGNF_RGF93CC50),
        PROJ4_LAMBERTIIE(12,
                R.string.solution_view_format_proj4_lambertIIextended,
                R.array.solution_view_coordinates_proj4_lambert93,
                CRSRegistry.IGNF_LAMBE),
       PROJ4_NAD83(13,
                R.string.solution_view_format_proj4_nad83,
                R.array.solution_view_coordinates_proj4_nad83,
                CRSRegistry.EPSG_4269),

        ECEF(14,
                R.string.solution_view_format_ecef,
                R.array.solution_view_coordinates_ecef,
                null),

        ENU_BASELINE(15,
                R.string.solution_view_format_enu_baseline,
                R.array.solution_view_coordinates_baseline_enu,
                null),

        PYL_BASELINE(16,
                R.string.solution_view_format_pyl_baseline,
                R.array.solution_view_coordinates_baseline_pyl,
                null),
        PROJ4_CUSTOM(17,
                R.string.solution_view_format_proj4_custom,
                R.array.solution_view_coordinates_proj4_custom,
                null)

        ;

        final int mStyledAttributeValue;
        final int mHeadersArrayId;
        final int mDescriptionId;
        final String mEPSGCode;

        private Format(int styledAttributeValue, int descriptionId, int HeadersArrayId, String EPSGCode) {
            mStyledAttributeValue = styledAttributeValue;
            mHeadersArrayId = HeadersArrayId;
            mDescriptionId = descriptionId;
            mEPSGCode = EPSGCode;
        }

        static Format valueOfStyledAttr(int val) {
            for (Format f: Format.values()) {
                if (f.mStyledAttributeValue == val)
                    return f;
            }
            throw new IllegalArgumentException();
        }

        public int getDescriptionResId() {
            return mDescriptionId;
        }
        public String getProj4String() {
            return mEPSGCode;
        }
    }

    public static final Format DEFAULT_SOLUTION_FORMAT = Format.WGS84;

    private final static int DEFAULT_COLOR_STATE_CLOSE = Color.DKGRAY;

    private Format mSolutionFormat;

    private boolean mBoolIsGeodetic = false;

    private final NumberFormat mCoordEcefFormatter;

    private final TextView mTextViewSolutionStatus;

    private final SolutionIndicatorView mSolutionIndicatorView;

    private final TextView mTextViewCoordinateSystem, mTextViewGeoidModel;
    private final TextView mTextViewCoord1Name, mTextViewCoord1Value;
    private final TextView mTextViewCoord2Name, mTextViewCoord2Value;
    private final TextView mTextViewCoord3Name, mTextViewCoord3Value;
    private final TextView mTextViewCoord4Name, mTextViewCoord4Value;

    private final TextView mTextViewCovariance;
    private final TextView mTextViewAge;
    private Proj4Converter proj4Converter = null;
    private GeoidModel model;
    public RtkCommon rtkCommon;
    private DemoModeLocation mDemoModeLocation;

    public SolutionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final Integer textColor;

        // process style attributes
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SolutionView, 0, 0);

        try {
            final int formatVal = a.getInt(R.styleable.SolutionView_format,
                    DEFAULT_SOLUTION_FORMAT.mStyledAttributeValue
                    );
            if (a.hasValue(R.styleable.SolutionView_textColor)) {
                textColor = a.getColor(R.styleable.SolutionView_textColor,
                        0);
            }else {
                textColor = null;
            }

            mSolutionFormat = Format.valueOfStyledAttr(formatVal);
        }finally {
            a.recycle();
        }

        inflate(context, R.layout.view_solution, this);

        mTextViewSolutionStatus = (TextView)findViewById(R.id.solution_status);
        mSolutionIndicatorView = (SolutionIndicatorView)findViewById(R.id.solution_indicator_view);
        mTextViewCoordinateSystem = (TextView)findViewById(R.id.coordinate_system);
        mTextViewGeoidModel = (TextView)findViewById(R.id.geoid_model);
        mTextViewCoord1Name = (TextView)findViewById(R.id.coord1_name);
        mTextViewCoord1Value = (TextView)findViewById(R.id.coord1_value);
        mTextViewCoord2Name = (TextView)findViewById(R.id.coord2_name);
        mTextViewCoord2Value = (TextView)findViewById(R.id.coord2_value);
        mTextViewCoord3Name = (TextView)findViewById(R.id.coord3_name);
        mTextViewCoord3Value = (TextView)findViewById(R.id.coord3_value);
        mTextViewCoord4Name = (TextView)findViewById(R.id.coord4_name);
        mTextViewCoord4Value = (TextView)findViewById(R.id.coord4_value);
        mTextViewCovariance = (TextView)findViewById(R.id.covariance_text);
        mTextViewAge = (TextView)findViewById(R.id.age_text);

        mCoordEcefFormatter = new DecimalFormat("0.000",
                DecimalFormatSymbols.getInstance(Locale.US));

        if (textColor != null) {
            ((TextView)findViewById(R.id.solution_title)).setTextColor(textColor);
            mTextViewSolutionStatus.setTextColor(textColor);
            mTextViewCoord1Name.setTextColor(textColor);
            mTextViewCoord1Value.setTextColor(textColor);
            mTextViewCoord2Name.setTextColor(textColor);
            mTextViewCoord2Value.setTextColor(textColor);
            mTextViewCoord3Name.setTextColor(textColor);
            mTextViewCoord3Value.setTextColor(textColor);
            mTextViewCoord4Name.setTextColor(textColor);
            mTextViewCoord4Value.setTextColor(textColor);
            mTextViewCovariance.setTextColor(textColor);
            mTextViewAge.setTextColor(textColor);
        }

        mDemoModeLocation = MainActivity.getDemoModeLocation();

        if (mDemoModeLocation.isInDemoMode()) {
            mSolutionFormat = Format.WGS84;
            updateCoordinatesHeader();
            setStats(new RtkControlResult());
        }else {
            updateCoordinatesHeader();
            clearCoordinates();
            setStats(new RtkControlResult());
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (rtkCommon!=null)
        {
            rtkCommon.closegeoid();
        }
        super.finalize();
    }

    public void setStats(RtkControlResult status) {
        final Solution sol = status.getSolution();
        int resId = 0;
        SolutionStatus solStatus = SolutionStatus.NONE;
        if (mDemoModeLocation.isInDemoMode() && RtkNaviService.mbStarted) {
            resId = SolutionStatus.INTERNAL.getNameResId();
            solStatus = SolutionStatus.INTERNAL;
        }else{
            resId = sol.getSolutionStatus().getNameResId();
            solStatus = sol.getSolutionStatus();
        }
        mTextViewSolutionStatus.setText(resId);
        mSolutionIndicatorView.setStatus(solStatus);
        updateCoordinates(status);
        updateAgeText(sol);
    }

    public void setFormat(Format format) {
        if (format != mSolutionFormat) {
            mSolutionFormat = format;
            updateCoordinatesHeader();
            clearCoordinates();
        }
    }

    public Format getFormat() {
        return mSolutionFormat;
    }

    private double getAltitudeCorrection(double lat, double lon)
    {
     // Gets if solution height is geodetic or ellipsoidal
        double dGeoidHeight = 0.0;
        String mSzEllipsoidal = this.getContext().getResources().getStringArray(R.array.solopt_height_entries)[0];
        SharedPreferences prefs= this.getContext().getSharedPreferences(SolutionOutputSettingsFragment.SHARED_PREFS_NAME, 0);
        String strHeightPref = prefs.getString(SolutionOutputSettingsFragment.KEY_HEIGHT, mSzEllipsoidal);
        if (strHeightPref.equals(mSzEllipsoidal))
        {
            dGeoidHeight = 0.0;
            mBoolIsGeodetic = false;
            model = null;
            mTextViewGeoidModel.setText(getResources().getStringArray(R.array.solopt_height_entries)[0]);
        }else
        {
            mBoolIsGeodetic = true;
            model = GeoidModel.valueOf( prefs.getString(SolutionOutputSettingsFragment.KEY_GEOID_MODEL,GeoidModel.EMBEDDED.name()) );
            mTextViewGeoidModel.setText(model.name());
            if (rtkCommon == null)
            {
                rtkCommon = new RtkCommon();
            }
            dGeoidHeight = rtkCommon.getAltitudeCorrection(lat, lon, model.getRtklibId());
        }
        return dGeoidHeight;

    }
    private void updateCoordinates(RtkControlResult rtk) {
        RtkCommon.Position3d roverPos;
        double Qe[];
        double dGeoidHeight = 0.0;
        final Solution sol = rtk.getSolution();
        Position3d roverEcefPos = new Position3d();
        RtkCommon.Matrix3x3 cov;


        double lat,lon,height;
        if (mDemoModeLocation.isInDemoMode() && RtkNaviService.mbStarted) {
            roverPos = mDemoModeLocation.getPosition();
            if (roverPos == null){
                return;
            }
            lat = roverPos.getLat();
            lon=roverPos.getLon();
            height=roverPos.getHeight();
            Qe = new double[9];
            Qe[4] = mDemoModeLocation.getNAccuracy();
            Qe[0] = mDemoModeLocation.getEAccuracy();
            Qe[8] = mDemoModeLocation.getVAccuracy();
            double[] covD = {0,0,0,0,0,0,0,0,0} ;
            cov = new Matrix3x3(covD);
            RtkCommon.pos2ecef(lat, lon, height, roverEcefPos);
        }else {
            roverEcefPos = sol.getPosition();
            if (RtkCommon.norm(roverEcefPos.getValues()) <= 0.0) {
                return;
            }
            roverPos = RtkCommon.ecef2pos(roverEcefPos);
            cov = sol.getQrMatrix();
            lat = roverPos.getLat();
            lon=roverPos.getLon();
            height=roverPos.getHeight();
            Qe = RtkCommon.covenu(lat, lon, cov).getValues();
        }
        double dlat = Math.toDegrees(lat);
        double dlon = Math.toDegrees(lon);
        ProjCoordinate proj4Coordinate;


        switch (mSolutionFormat) {
            case PROJ4_LAMBERT93:
            case PROJ4_LAMBERT93_CC43:
            case PROJ4_LAMBERT93_CC44:
            case PROJ4_LAMBERT93_CC45:
            case PROJ4_LAMBERT93_CC46:
            case PROJ4_LAMBERT93_CC47:
            case PROJ4_LAMBERT93_CC48:
            case PROJ4_LAMBERT93_CC49:
            case PROJ4_LAMBERT93_CC50:
            case PROJ4_LAMBERTIIE:
            case PROJ4_NAD83:
            case PROJ4_CUSTOM:
                if (proj4Converter == null)
                {
                    proj4Converter = new Proj4Converter();
                }
                if (mSolutionFormat.getProj4String() != null) {
                    proj4Coordinate = proj4Converter.convert(mSolutionFormat.getProj4String(), dlat, dlon);
                }else{
                    // if PROJ4_CUSTOM
                    SharedPreferences prefs= this.getContext().getSharedPreferences(SolutionOutputSettingsFragment.SHARED_PREFS_NAME, 0);
                    String strCustomProj4 = prefs.getString(SolutionOutputSettingsFragment.KEY_CUSTOM_PROJ4, getResources().getString(R.string.solopt_output_customproj4_default));
                    proj4Coordinate = proj4Converter.convert(strCustomProj4, dlat, dlon);
                }
                cov = sol.getQrMatrix();
                dGeoidHeight = getAltitudeCorrection(lat, lon);
                mTextViewCoord1Value.setText(String.format(Locale.US, "%.3f m", proj4Coordinate.x));
                mTextViewCoord2Value.setText(String.format(Locale.US, "%.3f m", proj4Coordinate.y));
                mTextViewCoord3Value.setText(String.format(Locale.US, "%.3f m el.", height-dGeoidHeight));
                if (mBoolIsGeodetic)
                {
                    mTextViewCoord3Name.setText(this.getContext().getResources().getStringArray(R.array.solution_view_coordinates_wgs84)[3]); //Altitude
                }else{
                    mTextViewCoord3Name.setText(this.getContext().getResources().getStringArray(R.array.solution_view_coordinates_wgs84)[2]); //Height
                }
                mTextViewCovariance.setText(String.format(
                        Locale.US,
                        "N:%6.3f\nE:%6.3f\nU:%6.3f m",
                        Math.sqrt(Qe[4] < 0 ? 0 : Qe[4]),
                        Math.sqrt(Qe[0] < 0 ? 0 : Qe[0]),
                        Math.sqrt(Qe[8] < 0 ? 0 : Qe[8])
                        ));
                break;
        case UTM:
            dGeoidHeight = getAltitudeCorrection(lat, lon);
            UTM myUTM = new UTM(dlat,dlon);
            if (proj4Converter == null)
            {
                proj4Converter = new Proj4Converter();
            }
            proj4Coordinate = proj4Converter.convert(myUTM.getCRSString(), dlat, dlon);
            mTextViewCoord1Value.setText(String.format(Locale.US, "%.3f m", proj4Coordinate.x));
            mTextViewCoord2Value.setText(String.format(Locale.US, "%.3f m", proj4Coordinate.y));
            mTextViewCoord3Value.setText(String.format(Locale.US, "%.3f m el.", height-dGeoidHeight));
            mTextViewCoord4Value.setText( myUTM.getUTMZone());
            if (mBoolIsGeodetic)
            {
                mTextViewCoord3Name.setText(this.getContext().getResources().getStringArray(R.array.solution_view_coordinates_wgs84)[3]); //Altitude
            }else{
                mTextViewCoord3Name.setText(this.getContext().getResources().getStringArray(R.array.solution_view_coordinates_wgs84)[2]); //Height
            }
            mTextViewCoord4Name.setText(this.getContext().getResources().getStringArray(R.array.solution_view_coordinates_utm)[3]); //Zone:
            mTextViewCovariance.setText(String.format(
                    Locale.US,
                    "N:%6.3f\nE:%6.3f\nU:%6.3f m",
                    Math.sqrt(Qe[4] < 0 ? 0 : Qe[4]),
                    Math.sqrt(Qe[0] < 0 ? 0 : Qe[0]),
                    Math.sqrt(Qe[8] < 0 ? 0 : Qe[8])
                    ));
            break;
        case WGS84:
        case WGS84_FLOAT:
            String strLat, strLon, strHeight,strAltitude="";

                if (mSolutionFormat == Format.WGS84) {
                    strLat = Deg2Dms.toString(Math.toDegrees(lat), true);
                    strLon = Deg2Dms.toString(Math.toDegrees(lon), false);
                }else {
                    strLat = String.format(Locale.US, "%11.8f°", Math.toDegrees(lat));
                    strLon = String.format(Locale.US, "%11.8f°", Math.toDegrees(lon));
                }

                dGeoidHeight = getAltitudeCorrection(lat, lon);

                if (mBoolIsGeodetic)
                {
                    mTextViewCoord4Name.setText(this.getContext().getResources().getStringArray(R.array.solution_view_coordinates_wgs84)[3]); //Altitude:

                }else
                {
                    mTextViewCoord4Name.setText("");
                }
                strAltitude = String.format(Locale.US, "%.3fm el.", height-dGeoidHeight);
                strHeight = String.format(Locale.US, "%.3fm el.", height);

            mTextViewCoord1Value.setText(strLat);
            mTextViewCoord2Value.setText(strLon);
            mTextViewCoord3Value.setText(strHeight);
            if (mBoolIsGeodetic)
            {
                mTextViewCoord4Value.setText(strAltitude);
            }else
            {
                mTextViewCoord4Value.setText("");
            }
            mTextViewCovariance.setText(String.format(
                    Locale.US,
                    "N:%6.3f\nE:%6.3f\nU:%6.3f m",
                    Math.sqrt(Qe[4] < 0 ? 0 : Qe[4]),
                    Math.sqrt(Qe[0] < 0 ? 0 : Qe[0]),
                    Math.sqrt(Qe[8] < 0 ? 0 : Qe[8])
                    ));
            break;
        case ECEF:
            final float[] qr = sol.getPositionVariance();
            mTextViewCoord1Value.setText(mCoordEcefFormatter.format(roverEcefPos.getX()));
            mTextViewCoord2Value.setText(mCoordEcefFormatter.format(roverEcefPos.getY()));
            mTextViewCoord3Value.setText(mCoordEcefFormatter.format(roverEcefPos.getZ()));
            mTextViewCovariance.setText(String.format(
                    Locale.US,
                    "X:%6.3f\nY:%6.3f\nZ:%6.3f m",
                    Math.sqrt(qr[0] < 0 ? 0 : qr[0]),
                    Math.sqrt(qr[1] < 0 ? 0 : qr[1]),
                    Math.sqrt(qr[2] < 0 ? 0 : qr[2])
                    ));
            break;
        case ENU_BASELINE:
        case PYL_BASELINE:
            final Position3d baseEcefPos;
            final Position3d baselineVector;
            final Position3d enu;
            final Position3d basePos;
            double baselineLen;
            String v1, v2, v3;

            baseEcefPos = rtk.getBasePosition();

            baselineVector = new Position3d(
                    roverEcefPos.getX() - baseEcefPos.getX(),
                    roverEcefPos.getY() - baseEcefPos.getY(),
                    roverEcefPos.getZ() - baseEcefPos.getZ()
                    );

            baselineLen = baselineVector.getNorm();
            if (baselineLen <= 0.0) {
                break;
            }

            basePos = RtkCommon.ecef2pos(baseEcefPos);
            enu = RtkCommon.ecef2enu(basePos.getLat(), basePos.getLon(), baselineVector);
            cov = sol.getQrMatrix();
            Qe = RtkCommon.covenu(basePos.getLat(), basePos.getLon(), cov).getValues();

            if (mSolutionFormat == Format.ENU_BASELINE) {
                v1 = String.format(Locale.US, "%.3f m", enu.getLat());
                v2 = String.format(Locale.US, "%.3f m", enu.getLon());
                v3 = String.format(Locale.US, "%.3f m", enu.getHeight());
            }else {
                double pitch, yaw;
                pitch = Math.asin(enu.getHeight()/baselineLen);
                yaw = Math.atan2(enu.getLat(), enu.getLon());
                if (yaw < 0.0) yaw += 2.0 * Math.PI;

                v1 = String.format(Locale.US, "%.3f °", Math.toDegrees(pitch));
                v2 = String.format(Locale.US, "%.3f °", Math.toDegrees(yaw));
                v3 = String.format(Locale.US, "%.3f m", baselineLen);
            }

            mTextViewCoord1Value.setText(v1);
            mTextViewCoord2Value.setText(v2);
            mTextViewCoord3Value.setText(v3);
            mTextViewCovariance.setText(String.format(
                    Locale.US,
                    "E:%6.3f\nN:%6.3f\nU:%6.3f m",
                    Math.sqrt(Qe[0] < 0 ? 0 : Qe[0]),
                    Math.sqrt(Qe[4] < 0 ? 0 : Qe[4]),
                    Math.sqrt(Qe[8] < 0 ? 0 : Qe[8])
                    ));
            break;
        default:
            throw new IllegalStateException();
        }
    }

    private void clearCoordinates() {
        mTextViewCoord1Value.setText("");
        mTextViewCoord2Value.setText("");
        mTextViewCoord3Value.setText("");
    }

    private void updateAgeText(Solution sol) {
        String format = getResources().getString(R.string.solution_view_age_text_format);
        if (mDemoModeLocation.isInDemoMode() && RtkNaviService.mbStarted) {

            mTextViewAge.setText(String.format(
                    Locale.US,
                    format,
                    mDemoModeLocation.getAge(),
                    0.0,
                    mDemoModeLocation.getNbSat()
                    ));

        }else{

            mTextViewAge.setText(String.format(
                    Locale.US,
                    format,
                    sol.getAge(),
                    sol.getRatio(),
                    sol.getNs()
                    ));
        }

    }

    private void updateCoordinatesHeader() {
        final String headers[];
        if (isInEditMode()) {
            headers = new String[] {"Lat:", "Lon:", "Height:", "Altitude:"};
        }else {
            headers = getResources().getStringArray(mSolutionFormat.mHeadersArrayId);
        }
        mTextViewCoordinateSystem.setText(getResources().getString(mSolutionFormat.getDescriptionResId()));
        if (model != null)
        {
            mTextViewGeoidModel.setText(model.name());
        }
        mTextViewCoord1Name.setText(headers[0]);
        mTextViewCoord2Name.setText(headers[1]);
        mTextViewCoord3Name.setText(headers[2]);
        if (headers.length == 4)
        {
            mTextViewCoord4Name.setText(headers[3]);
        }else{
            mTextViewCoord4Name.setText("");
            mTextViewCoord4Value.setText("");
        }
    }

    public static class SolutionIndicatorView extends View {

        private final static int DEFAULT_INDICATOR_WIDTH = 9;

        private final static int DEFAULT_INDICATOR_HEIGHT = 9;

        private final Paint mIndicatorPaint;

        private float mIndicatorWidth, mIndicatorHeight;

        private SolutionStatus mStatus;

        public SolutionIndicatorView(Context context, AttributeSet attrs) {
            super(context, attrs);

            final float density = getResources().getDisplayMetrics().density;

            mIndicatorWidth = DEFAULT_INDICATOR_WIDTH * density;
            mIndicatorHeight = DEFAULT_INDICATOR_HEIGHT * density;

            mStatus = SolutionStatus.NONE;

            mIndicatorPaint = new Paint();
            mIndicatorPaint.setColor(DEFAULT_COLOR_STATE_CLOSE);
        }

        public void setStatus(SolutionStatus status) {
            mStatus = status;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(measureWidth(widthMeasureSpec),
                    measureHeight(heightMeasureSpec));
        }

        private int measureWidth(int measureSpec) {
            int result = 0;
            int specMode = MeasureSpec.getMode(measureSpec);
            int specSize = MeasureSpec.getSize(measureSpec);

            if (specMode == MeasureSpec.EXACTLY) {
                // We were told how big to be
                result = specSize;
            } else {
                // Measure the text
                result = (int)(mIndicatorWidth + getPaddingLeft() + getPaddingRight());
                if (specMode == MeasureSpec.AT_MOST) {
                    // Respect AT_MOST value if that was what is called for by measureSpec
                    result = Math.min(result, specSize);
                }
            }

            return result;
        }

        private int measureHeight(int measureSpec) {
            int result = 0;
            int specMode = MeasureSpec.getMode(measureSpec);
            int specSize = MeasureSpec.getSize(measureSpec);

            if (specMode == MeasureSpec.EXACTLY) {
                // We were told how big to be
                result = specSize;
            } else {
                // Measure the text (beware: ascent is a negative number)
                result = (int)(mIndicatorHeight + getPaddingTop() + getPaddingBottom());
                if (specMode == MeasureSpec.AT_MOST) {
                    // Respect AT_MOST value if that was what is called for by measureSpec
                    result = Math.min(result, specSize);
                }
            }
            return result;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float x, y;

            super.onDraw(canvas);

            x = getPaddingTop();
            y = getPaddingLeft();

            mIndicatorPaint.setColor(getIndicatorColor(mStatus));
            canvas.drawRect(x, y, x+mIndicatorWidth,
                    y+mIndicatorHeight, mIndicatorPaint);

        }

        public static int getIndicatorColor(SolutionStatus status) {
            int c;

            switch (status) {
            case NONE:
                c = DEFAULT_COLOR_STATE_CLOSE;
                break;
            case FIX:
                c = Color.GREEN;
                break;
            case FLOAT:
                c = Color.rgb(255, 127, 0);
                break;
            case SBAS:
                c = Color.rgb(255, 0, 255);
                break;
            case DGPS:
                c = Color.BLUE;
                break;
            case SINGLE:
                c = Color.RED;
                break;
            case PPP:
                c = Color.rgb(0, 128, 128);
                break;
            case DR:
                c = Color.YELLOW;
                break;
            case INTERNAL:
                c= Color.MAGENTA;
                break;
            default:
                c = Color.WHITE;
                break;
            }
            return c;
        }


    }

}
