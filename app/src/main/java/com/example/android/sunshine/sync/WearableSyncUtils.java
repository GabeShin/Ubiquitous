package com.example.android.sunshine.sync;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.utilities.SunshineWeatherUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import static android.R.attr.path;

/**
 * Created by Gabe on 2017-01-19.
 */

public class WearableSyncUtils implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks{

    private static String LOG_TAG=WearableSyncUtils.class.getSimpleName();

    private PutDataMapRequest mPutDataMapRequest;
    private Context mContext;

    private GoogleApiClient mGoogleApiClient;

    private static WearableSyncUtils wearableSyncUtils;

    private WearableSyncUtils(){ }

    public static synchronized WearableSyncUtils getInstance( ) {
        if (wearableSyncUtils == null)
            wearableSyncUtils =new WearableSyncUtils();
        return wearableSyncUtils;
    }

    public void initialize(Context context, PutDataMapRequest putDataMapRequest) {
        mPutDataMapRequest = putDataMapRequest;
        mContext = context;

        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        if (!mGoogleApiClient.isConnected()){
            mGoogleApiClient.connect();
        }
    }

    void sendWeatherData() {
        PutDataRequest putDataRequest = mPutDataMapRequest.asPutDataRequest().setUrgent();

        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest).
                setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(final DataApi.DataItemResult result) {
                        if(result.getStatus().isSuccess()) {
                            Log.d("result", "Data item set: " + result.getDataItem().getUri());
                        }
                    }
                });
        Log.d("Mobile-WearableDataSync", "Items transferred" );
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (bundle != null) {
            Log.d("Mobile-WearableDataSync", "mobile side onConnected" + bundle.toString());
        }else {
            Log.d("Mobile-WearableDataSync", "mobile side onConnected Bundle is null" );
        }
        Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(onConnectedResultCallBack);

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("Mobile-WearableDataSync", "mobile side suspended : " + i);

    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d("Mobile-WearableDataSync", "mobile side onConnectionFailed : " + connectionResult);

    }
    private final ResultCallback<DataItemBuffer> onConnectedResultCallBack = new ResultCallback<DataItemBuffer>() {
        @Override
        public void onResult(@NonNull DataItemBuffer dataItems) {
            Log.i(LOG_TAG, "Result Callback : " + String.valueOf(dataItems));
        }
    };
}