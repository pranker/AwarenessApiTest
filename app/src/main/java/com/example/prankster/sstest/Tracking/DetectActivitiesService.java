package com.example.prankster.sstest.Tracking;

/**
 * Created by prankster on 2016-09-05.
 */

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.example.prankster.sstest.BuildConfig;
import com.example.prankster.sstest.DataBase.DBHelper;
import com.example.prankster.sstest.MainActivity;
import com.example.prankster.sstest.R;
import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.fence.AwarenessFence;
import com.google.android.gms.awareness.fence.DetectedActivityFence;
import com.google.android.gms.awareness.fence.FenceQueryRequest;
import com.google.android.gms.awareness.fence.FenceQueryResult;
import com.google.android.gms.awareness.fence.FenceState;
import com.google.android.gms.awareness.fence.FenceStateMap;
import com.google.android.gms.awareness.fence.FenceUpdateRequest;
import com.google.android.gms.awareness.fence.HeadphoneFence;
import com.google.android.gms.awareness.state.HeadphoneState;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.util.Arrays;
import java.util.Date;

import static android.provider.Settings.System.DATE_FORMAT;

public class DetectActivitiesService extends Service implements GoogleApiClient.OnConnectionFailedListener {
    DBHelper dbHelper;

    //교통수단으로 이동중인지, 아닌지 노티 주기 위한 변수들
    NotificationManager Notifi_M;
    Notification Notifi ;

    //Awareness Fence 사용을 위한 변수들
    private GoogleApiClient uApiClient;
    private PendingIntent mPendingIntent;
    private ActivityFenceReceiver mActivityFenceReceiver;

    // The fence key is how callback code determines which fence fired.
    private final String FENCE_KEY = "detect_activity_fence_key", TAG = getClass().getSimpleName();
    // The intent action which will be fired when your fence is triggered.
    private final String FENCE_RECEIVER_ACTION = BuildConfig.APPLICATION_ID + "FENCE_RECEIVER_ACTION";

    //서비스 동작중인지 확인하기 위한 쓰레드
    DetectActivitiesServiceThread thread;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(uApiClient==null){
            setupService();
            setupClient();
        }

        return START_STICKY;
    }

    private void setupService() {
        dbHelper = new DBHelper(getApplicationContext(), "MyInfo.db", null, 1);
        Notifi_M = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        myServiceHandler handler = new myServiceHandler();
        thread = new DetectActivitiesServiceThread(handler);
        thread.start();
    }

    private void setupClient() {
        uApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Awareness.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        Log.d("MYTEST","DetectActivitiesService : Awareness API 를 등록했습니다.");
                        Intent intent2 = new Intent(FENCE_RECEIVER_ACTION);
                        mPendingIntent =
                                PendingIntent.getBroadcast(DetectActivitiesService.this, 0, intent2, 0);
                        // The broadcast receiver that will receive intents when a fence is triggered.
                        mActivityFenceReceiver = new ActivityFenceReceiver();
                        registerReceiver(mActivityFenceReceiver, new IntentFilter(FENCE_RECEIVER_ACTION));
                        setupVehicleFences();
                    }
                    @Override
                    public void onConnectionSuspended(int i) {
                    }
                })
                .build();
        uApiClient.connect();
    }

    /**
     * Sets up {@link AwarenessFence}'s for the sample app, and registers callbacks for them
     * with a custom {@link BroadcastReceiver}
     */
    private void setupVehicleFences() {
        //AwarenessFence vehicleFence = DetectedActivityFence.during(DetectedActivityFence.WALKING);
        //AwarenessFence vehicleFence = DetectedActivityFence.during(DetectedActivityFence.IN_VEHICLE);
        AwarenessFence vehicleFence = HeadphoneFence.during(HeadphoneState.PLUGGED_IN);
        // Register the fence to receive callbacks.
        Awareness.FenceApi.updateFences(
                uApiClient,
                new FenceUpdateRequest.Builder()
                        .addFence(FENCE_KEY, vehicleFence, mPendingIntent)
                        .build())
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if(status.isSuccess()) {
                            Log.d("MYTEST", "교통수단 펜스를 등록했습니다.");
                        } else {
                            Log.e("MYTEST", "에러. 교통수단 펜스 등록 안됨: " + status);
                        }
                    }
                });
    }
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d("MYTEST","DetectActivitiesService : Google API Client Connection fail");
    }

    class myServiceHandler extends Handler {
        @Override
        public void handleMessage(android.os.Message msg) {
            Toast.makeText(DetectActivitiesService.this, "DetectActivitiesService 동작중", Toast.LENGTH_LONG).show();
        }
    };

    public class ActivityFenceReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (!TextUtils.equals(FENCE_RECEIVER_ACTION, intent.getAction())) {
                Log.e("MYTEST","ActivityFenceReceiver에서 지원하지 않는 액션을 받았습니다.");
                return;
            }
            // The state information for the given fence is em
            FenceState fenceState = FenceState.extract(intent);

            if (TextUtils.equals(fenceState.getFenceKey(), FENCE_KEY)) {
                String fenceStateStr;
                int state; // 1:TRUE,  2:FALSE , 3:UNKNOWN
                switch (fenceState.getCurrentState()) {

                    case FenceState.TRUE:
                        state = 1;
                        fenceStateStr = "운송수단 이용중. 병원을 찾아가는 중이라고 가정합니다.";
                        break;
                    case FenceState.FALSE:
                        state = 2;
                        fenceStateStr = "운송수단 사용 종료. 500m 내의 병원을 펜스 등록.";
                        break;
                    case FenceState.UNKNOWN:
                        state = 3;
                        fenceStateStr = "unknown";
                        break;
                    default:
                        state = 4;
                        fenceStateStr = "unknown value";
                }
                notifyVehicleResult(fenceStateStr);
                runDetectedHospitalService(state);

                //펜스 쿼리 확인
                queryFence(FENCE_KEY);
            }
        }
    }

    private void notifyVehicleResult(String fenceStateStr) {
        Intent intent2 = new Intent(DetectActivitiesService.this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(DetectActivitiesService.this, 0, intent2, PendingIntent.FLAG_UPDATE_CURRENT);

        Notifi = new Notification.Builder(getApplicationContext())
                .setContentTitle("Activities 알림")
                .setContentText(fenceStateStr)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker("알림!!!")
                .setContentIntent(pendingIntent)
                .build();

        //소리추가
        Notifi.defaults = Notification.DEFAULT_SOUND;

        //알림 소리를 한번만 내도록
        Notifi.flags = Notification.FLAG_ONLY_ALERT_ONCE;

        //확인하면 자동으로 알림이 제거 되도록
        Notifi.flags = Notification.FLAG_AUTO_CANCEL;

        Notifi_M.notify( 777 , Notifi);
        Log.d("MYTEST: " , fenceStateStr);
    }

    private void runDetectedHospitalService(int currentState) {
        String curStatus = dbHelper.getStatus();

        //운전중이거나 교통수단 이용중인 상태에서 -> 이용중이지 않은 상태로 바뀔 경우,
        //병원을 펜스에 등록하고 병원에 근접했는지 확인하기 위한 DetectHospitalService  를 시작한다.
        if(curStatus.equals("VEHICLE") && currentState==2){
            Intent hospitalIntent = new Intent(DetectActivitiesService.this, DetectHospitalService.class);
            Log.i("MYTEST","DetectHospitalService를 시작합니다. ");

            startService(hospitalIntent);
        }
        dbHelper.updateStatus(currentState);
        Log.i("MYTEST","Activity 상태 변경 확인:"+curStatus+"-->"+dbHelper.getStatus());

    }

    protected void queryFence(final String fenceKey) {
        Awareness.FenceApi.queryFences(uApiClient,
                FenceQueryRequest.forFences(Arrays.asList(fenceKey)))
                .setResultCallback(new ResultCallback<FenceQueryResult>() {
                    @Override
                    public void onResult(@NonNull FenceQueryResult fenceQueryResult) {
                        if (!fenceQueryResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Could not query fence: " + fenceKey);
                            return;
                        }
                        FenceStateMap map = fenceQueryResult.getFenceStateMap();
                        for (String fenceKey : map.getFenceKeys()) {
                            FenceState fenceState = map.getFenceState(fenceKey);
                            Log.i("MYTEST", "Fence " + fenceKey + ": "
                                    + fenceState.getCurrentState()
                                    + ", was="
                                    + fenceState.getPreviousState()
                                    + ", lastUpdateTime="
                                    + DATE_FORMAT.format(
                                    String.valueOf(new Date(fenceState.getLastFenceUpdateTimeMillis()))));
                        }
                    }
                });
    }

    //서비스가 종료될 때 할 작업
    public void onDestroy() {
        thread.stopForever();
        thread = null;//쓰레기 값을 만들어서 빠르게 회수하라고 null을 넣어줌.
    }
}
