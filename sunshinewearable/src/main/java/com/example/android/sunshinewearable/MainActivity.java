package com.example.android.sunshinewearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static android.R.attr.centerX;
import static android.R.attr.centerY;

public class MainActivity extends CanvasWatchFaceService {

    private String LOG_TAG = MainActivity.class.getSimpleName();

    private static final String WEATHER_MAX_TEMP = "wearable.weather.max_temp";
    private static final String WEATHER_MIN_TEMP = "wearable.weather.min_temp";
    private static final String WEATHER_HUMIDITY = "wearable.weather.humidity";
    private static final String WEATHER_PRESSURE = "wearable.weather.pressure";
    private static final String WEATHER_WIND_SPEED = "wearable.weather.wind_speed";
    private static final String WEATHER_CONDITION_ID = "wearable.weather.condition_id";
    private static final String WEATHER_CONDITION_ICON = "wearable.weather.condition_icon";
    private static final String WEATHER_WIND_DEGREE = "wearable.weather.wind_degree";

    private static final Double WEATHER_DEFAULT_VALUE = 0.0d;
    private static final int WEATHER_CONDITION_DEFAULT_ID = R.drawable.ic_clear;
    private static final String PATH_WITH_WEATHER_DATA = "/weather";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);


    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MainActivity.Engine> mWeakReference;

        public EngineHandler(MainActivity.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        boolean mAmbient;
        Context mContext;

        Calendar mCalendar;

        //String for get weather Info from mobileApp.

        String mWeatherText = "weather";//weather condition
        Drawable mWeatherIcon; //weather Icon
        int mWeatherConditionId;
        int mWeatherIconId = -1;
        double mMaxTemp;
        double mMinTemp;
        double mPressures;
        double mWindSpeed;
        double mWindDegree;
        double mHumidity;


        GoogleApiClient mGoogleApiClient; //GoogleClient for get Data from Mobile.


        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mContext = MainActivity.this.getApplicationContext();

            setWatchFaceStyle(new WatchFaceStyle.Builder(MainActivity.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MainActivity.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.colorPrimaryDark));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mWeatherIcon = resources.getDrawable(R.drawable.ic_cc_clear);

            mCalendar = Calendar.getInstance();

            //setting GoogleApi

            mGoogleApiClient = new GoogleApiClient.Builder(MainActivity.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();


        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
            }
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                releaseGoogleApiClient();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void releaseGoogleApiClient() {
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MainActivity.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MainActivity.this.unregisterReceiver(mTimeZoneReceiver);
        }
        boolean isRound;
        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MainActivity.this.getResources();
            isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.tap_toast, Toast.LENGTH_SHORT)
                            .show();

                    Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
                    updateWeatherData();

                    Log.d(LOG_TAG,"updateWeatherData");
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            float centerX = bounds.width() /2f;
            float centerY = bounds.height() /2f;

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            SimpleDateFormat simpleTimeFormat = new SimpleDateFormat("HH:mm");
            String timeText = simpleTimeFormat.format(mCalendar.getTime());
            Paint weatherTextPaint = createTextPaint(Color.WHITE);

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, MMM DD yyyy");
            String dateText = simpleDateFormat.format(mCalendar.getTime());
            Paint dateTextPaint = createTextPaint(Color.LTGRAY);
            dateTextPaint.setTextSize(20f);

            String highTempAlly = formatTemperature(mContext, mMaxTemp);;
            String minTempAlly = formatTemperature(mContext, mMinTemp);

            Paint titlePaint = createTextPaint(Color.WHITE);
            titlePaint.setTextSize(20f);

            weatherTextPaint.setTextSize(12f);

            Paint highTemptTextPaint = createTextPaint(Color.WHITE);
            highTemptTextPaint.setTextSize(35f);

            Paint lowTempTextPaint = createTextPaint(Color.LTGRAY);
            lowTempTextPaint.setTextSize(35f);

            Paint linePaint = new Paint();
            linePaint.setColor(Color.LTGRAY);

            if (!isRound) {
                canvas.drawText(timeText, centerX - (mTextPaint.measureText(timeText)/2f), centerY - 45f, mTextPaint);
                canvas.drawText(dateText, centerX - dateTextPaint.measureText(dateText)/2f, centerY - 5f, dateTextPaint);
                canvas.drawLine(centerX - 25f, centerY + 31f, centerX + 25f, centerY + 31f, linePaint);
                canvas.drawText(highTempAlly, centerX - 15f, centerY + 90f, highTemptTextPaint);
                canvas.drawText(minTempAlly, centerX + 45f, centerY + 90f, lowTempTextPaint);
                Bitmap bitmap;

                if (mWeatherIconId != -1) {
                    bitmap = BitmapFactory.decodeResource(getResources(), mWeatherIconId);
                    canvas.drawBitmap(bitmap, centerX - 95f, centerY + 50f, null);
                }
            } else {
                canvas.drawText(timeText, centerX - (mTextPaint.measureText(timeText)/2f), centerY - 60f, mTextPaint);
                canvas.drawText(dateText, centerX - dateTextPaint.measureText(dateText)/2f, centerY - 20f, dateTextPaint);
                canvas.drawLine(centerX - 25f, centerY + 16f, centerX + 25f, centerY + 16f, linePaint);
                canvas.drawText(highTempAlly, centerX - 15f, centerY + 75f, highTemptTextPaint);
                canvas.drawText(minTempAlly, centerX + 45f, centerY + 75f, lowTempTextPaint);
                Bitmap bitmap;

                if (mWeatherIconId != -1) {
                    bitmap = BitmapFactory.decodeResource(getResources(), mWeatherIconId);
                    canvas.drawBitmap(bitmap, centerX - 95f, centerY + 35f, null);
                }
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {

            if (bundle != null) {
                Log.d("Wearable-WearService", "onConnected : " + bundle.toString());
            }else {
                Log.d("Wearable-WearService", "onConnected : bundle is null ");
            }

            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateWeatherData();
        }

        private void updateWeatherData() {
            Log.d("Wearable-WearService", "updateWeatherData ");

            fetchWeatherDataMap(mGoogleApiClient,
                    new FetchWeatherDataMapCallback() {
                        @Override
                        public void onWeatherDataFetched(DataMap weatherData) {
                            Log.d("Wearable-WearService", "onWeatherDataFetched ");

                            setDefaultValuesForMissingWeatherKeys(weatherData);
                            putWeatherDataItem(mGoogleApiClient, weatherData);

                            updateUiForWeatherDataMap(weatherData);
                        }
                    });

        }
        private void setDefaultValuesForMissingWeatherKeys(DataMap weatherData){
            Log.d("Wearable-WearService", "startsetDefaultvaluesForMissingWeatherKeys ");

            addDataKeyIfMissing(weatherData, WEATHER_MAX_TEMP,
                    WEATHER_DEFAULT_VALUE);
            addDataKeyIfMissing(weatherData, WEATHER_MIN_TEMP,
                    WEATHER_DEFAULT_VALUE);
            addDataKeyIfMissing(weatherData, WEATHER_CONDITION_ID,
                    WEATHER_CONDITION_DEFAULT_ID);
            addDataKeyIfMissing(weatherData, WEATHER_HUMIDITY,
                    WEATHER_DEFAULT_VALUE);
            addDataKeyIfMissing(weatherData, WEATHER_PRESSURE,
                    WEATHER_DEFAULT_VALUE);
            addDataKeyIfMissing(weatherData, WEATHER_WIND_SPEED,
                    WEATHER_DEFAULT_VALUE);

        }

        private void addDataKeyIfMissing(DataMap weatherData, String key, Double weatherValues){
            Log.d("Wearable-WearService", "addDataKeyIfMissing ");

            if (!weatherData.containsKey(key)){
                Log.d("Wearable-WearService", key + " is missing add to data");

                weatherData.putDouble(key, weatherValues);
            }
        }
        private void addDataKeyIfMissing(DataMap weatherData, String key, int weatherValues){
            if (!weatherData.containsKey(key)){
                Log.d("Wearable-WearService", key + " is missing add to data");

                weatherData.putInt(key, weatherValues);
            }
        }

        private Asset createAssetFromBitmap(Bitmap bitmap) {
            final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("Wearable-WearService", "Suspended : " + i);

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d("Wearable-WearService", "onConnectionFailed : " + connectionResult);

        }


        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if (!dataItem.getUri().getPath().equals(PATH_WITH_WEATHER_DATA)) {

                        DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                        DataMap weatherData = dataMapItem.getDataMap();
                        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                            Log.d(LOG_TAG, "weather Data Updated: " + weatherData);
                        }
                        updateUiForWeatherDataMap(weatherData);
                    }
                }
            }
        }

        private void updateUiForWeatherDataMap(DataMap weatherData) {
            boolean uiUpdated = false;
            for (String weatherKey : weatherData.keySet()){
                if (!weatherData.containsKey(weatherKey)){
                    continue;
                }
                switch (weatherKey){
                    case WEATHER_CONDITION_ID:
                        mWeatherConditionId = weatherData.getInt(weatherKey);
                        mWeatherIconId = getSmallArtResourceIdForWeatherCondition(mWeatherConditionId);
                        break;
                    case WEATHER_MAX_TEMP:
                        mMaxTemp = weatherData.getDouble(weatherKey);
                        break;
                    case WEATHER_MIN_TEMP:
                        mMinTemp = weatherData.getDouble(weatherKey);
                        break;
                }
            }
            Log.d(LOG_TAG, "WeatherId = " + mWeatherConditionId + "\n"
                    + "MaxTemp = " + mMaxTemp+ "\n"
                    + "MinTemp = " + mMinTemp+ "\n"
            );

            invalidate();
        }
    }

    public int getSmallArtResourceIdForWeatherCondition(int weatherId) {
        /*
         * Based on weather code data for Open Weather Map.
         */
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 771 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        } else if (weatherId >= 900 && weatherId <= 906) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 958 && weatherId <= 962) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 951 && weatherId <= 957) {
            return R.drawable.ic_clear;
        }

        Log.e(LOG_TAG, "Unknown Weather: " + weatherId);
        return R.drawable.ic_storm;
    }
    public static String formatTemperature(Context context, double temperature) {

        int temperatureFormatResourceId = R.string.format_temperature;

        /* For presentation, assume the user doesn't care about tenths of a degree. */
        return String.format(context.getString(temperatureFormatResourceId), temperature);
    }
    public static String getFormattedWind(Context context, float windSpeed, float degrees) {
        int windFormat = R.string.format_wind_kmh;


        /*
         * You know what's fun? Writing really long if/else statements with tons of possible
         * conditions. Seriously, try it!
         */
        String direction = "Unknown";
        if (degrees >= 337.5 || degrees < 22.5) {
            direction = "N";
        } else if (degrees >= 22.5 && degrees < 67.5) {
            direction = "NE";
        } else if (degrees >= 67.5 && degrees < 112.5) {
            direction = "E";
        } else if (degrees >= 112.5 && degrees < 157.5) {
            direction = "SE";
        } else if (degrees >= 157.5 && degrees < 202.5) {
            direction = "S";
        } else if (degrees >= 202.5 && degrees < 247.5) {
            direction = "SW";
        } else if (degrees >= 247.5 && degrees < 292.5) {
            direction = "W";
        } else if (degrees >= 292.5 && degrees < 337.5) {
            direction = "NW";
        }

        return String.format(context.getString(windFormat), windSpeed, direction);
    }
    public void putWeatherDataItem(GoogleApiClient googleApiClient, DataMap newWeatherData) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WITH_WEATHER_DATA);
        putDataMapRequest.setUrgent();
        DataMap weatherToPut = putDataMapRequest.getDataMap();
        weatherToPut.putAll(newWeatherData);
        Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                        Log.d(LOG_TAG, "putDataItem result status: " + dataItemResult.getStatus());
                    }
                });
    }

    public static void fetchWeatherDataMap(final GoogleApiClient client,
                                           final FetchWeatherDataMapCallback callback) {
        Wearable.NodeApi.getLocalNode(client).setResultCallback(
                new ResultCallback<NodeApi.GetLocalNodeResult>() {
                    @Override
                    public void onResult(@NonNull NodeApi.GetLocalNodeResult getLocalNodeResult) {
                        String localNode = getLocalNodeResult.getNode().getId();
                        Uri uri = new Uri.Builder().scheme("wear")
                                .path(PATH_WITH_WEATHER_DATA)
                                .authority(localNode)
                                .build();
                        Wearable.DataApi.getDataItem(client, uri)
                                .setResultCallback(new DataItemResultCallback(callback));
                    }
                }
        );
    }

    public interface FetchWeatherDataMapCallback {
        void onWeatherDataFetched(DataMap weatherData);
    }

    private static class DataItemResultCallback implements ResultCallback<DataApi.DataItemResult> {

        private final FetchWeatherDataMapCallback mCallback;

        public DataItemResultCallback(FetchWeatherDataMapCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
            if (dataItemResult.getStatus().isSuccess()) {
                if (dataItemResult.getDataItem() != null) {
                    DataItem weatherDataItem = dataItemResult.getDataItem();
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(weatherDataItem);
                    DataMap weatherData = dataMapItem.getDataMap();
                    mCallback.onWeatherDataFetched(weatherData);
                } else {
                    mCallback.onWeatherDataFetched(new DataMap());
                }
            }

        }
    }

}
