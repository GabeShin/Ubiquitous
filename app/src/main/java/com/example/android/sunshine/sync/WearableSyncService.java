package com.example.android.sunshine.sync;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import static com.example.android.sunshine.DetailActivity.INDEX_WEATHER_CONDITION_ID;
import static com.example.android.sunshine.DetailActivity.INDEX_WEATHER_MAX_TEMP;
import static com.example.android.sunshine.DetailActivity.INDEX_WEATHER_MIN_TEMP;
import static com.example.android.sunshine.DetailActivity.WEATHER_DETAIL_PROJECTION;

/**
 * Created by Gabe on 2017-01-19.
 */

public class WearableSyncService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    public static final String WEATHER_MAX_TEMP = "wearable.weather.max_temp";
    public static final String WEATHER_MIN_TEMP = "wearable.weather.min_temp";
    public static final String WEATHER_CONDITION_ID = "wearable.weather.condition_id";

    private static final String LOG_TAG = WearableSyncService.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;
    private boolean mIsConnected = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mIsConnected = true;
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("Mobile-WearableDataSync", "mobile side suspended : " + i);
        mIsConnected = false;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d("Mobile-WearableDataSync", "mobile side onConnectionFailed : " + connectionResult);
        mIsConnected = false;
    }

    private final ResultCallback<DataItemBuffer> onConnectedResultCallBack = new ResultCallback<DataItemBuffer>() {
        @Override
        public void onResult(@NonNull DataItemBuffer dataItems) {
            Log.i(LOG_TAG, "Result Callback : " + String.valueOf(dataItems));
        }
    };

    /**
     *
     * @param dataToSend
     */

    private final String WEARABLE_PATH = "/weather";

    public void sendDataToWearable() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!mIsConnected){
                    Log.e(LOG_TAG, "Failed to connect to mGoogleApiClient");
                    mGoogleApiClient.blockingConnect();
                }

                if (mGoogleApiClient.isConnected()){
                    // get the cursor
                    String selection = "";
                    String[] selectionArgs = new String[]{};

                    Cursor cursor = getContentResolver().query(WeatherContract.WeatherEntry.CONTENT_URI
                            , WEATHER_DETAIL_PROJECTION
                            , selection
                            , selectionArgs,
                            null);

                    PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEARABLE_PATH);

                    if (cursor.moveToFirst()) {
                        double maxTemp = cursor.getDouble(INDEX_WEATHER_MAX_TEMP);
                        double minTemp = cursor.getDouble(INDEX_WEATHER_MIN_TEMP);
                        int conditionId = cursor.getInt(INDEX_WEATHER_CONDITION_ID);

                        // put DataItem into the DataMap

                        Log.d(LOG_TAG, "maxTemp : " + maxTemp + "\n"
                                + "minTemp : " + minTemp + "\n"
                                + "conditionId : " + conditionId + "\n");

                        putDataMapRequest.getDataMap().putDouble(WEATHER_MAX_TEMP, maxTemp);
                        putDataMapRequest.getDataMap().putDouble(WEATHER_MIN_TEMP, minTemp);
                        putDataMapRequest.getDataMap().putInt(WEATHER_CONDITION_ID, conditionId);
                    }

                    PutDataRequest request = putDataMapRequest.asPutDataRequest();
                    PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, request);
                    pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                            Log.e("WEAR APP", "APPLICATION Result has come");
                        }
                    });
                } else {
                    Log.e(LOG_TAG, "No Google API Client connection");
                }
            }
        }).start();
    }
}