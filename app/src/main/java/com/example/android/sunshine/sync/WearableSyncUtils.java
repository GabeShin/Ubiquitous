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
import com.google.android.gms.wearable.DataMap;
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

public class WearableSyncUtils extends Thread{

    String path;
    DataMap dataMap;
    GoogleApiClient mApiClient;


    public WearableSyncUtils(String p, DataMap data, GoogleApiClient apiClient){

        path = p;
        dataMap = data;
        mApiClient=apiClient;

    }

    public void run(){

        PutDataMapRequest putDMR = PutDataMapRequest.create(path);
        putDMR.getDataMap().putAll(dataMap);
        PutDataRequest request = putDMR.asPutDataRequest();
        DataApi.DataItemResult result = Wearable.DataApi.putDataItem(mApiClient, request).await();
        if (result.getStatus().isSuccess()) {
            Log.d("WATCH_MESSAGE", "DataMap: " + dataMap + " sent successfully to data layer ");
        }
        else {
            // Log an error
            Log.d("WATCH_MESSAGE", "ERROR: failed to send DataMap to data layer");
        }

    }

}