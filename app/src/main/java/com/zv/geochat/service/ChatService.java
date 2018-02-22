package com.zv.geochat.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.zv.geochat.Constants;
import com.zv.geochat.R;
import com.zv.geochat.model.ChatMessage;
import com.zv.geochat.notification.NotificationDecorator;
import com.zv.geochat.provider.ChatMessageStore;

public class ChatService extends Service {
    private static final String TAG = "ChatService";

    public static final String CMD = "msg_cmd";
    public static final int CMD_JOIN_CHAT = 10;
    public static final int CMD_LEAVE_CHAT = 20;
    public static final int CMD_SEND_MESSAGE = 30;
    public static final int CMD_RECEIVE_MESSAGE = 40;
    public static final String KEY_MESSAGE_TEXT = "message_text";

    private NotificationManager notificationMgr;
    private PowerManager.WakeLock wakeLock;
    private NotificationDecorator notificationDecorator;
    private ChatMessageStore chatMessageStore;

    private String myName;
    private long time = 0;
    private long currentSessionTime = 0;
    CountDownTimer timer;

    public ChatService() {
    }

    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate()");
        super.onCreate();
        notificationMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationDecorator = new NotificationDecorator(this, notificationMgr);
        chatMessageStore = new ChatMessageStore(this);
        loadUserNameFromPreferences();
        loadTimerFromPreferences();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand()");
        super.onStartCommand(intent, flags, startId);
        if (intent != null) {
            Bundle data = intent.getExtras();
            handleData(data);
            if (!wakeLock.isHeld()) {
                Log.v(TAG, "acquiring wake lock");
                wakeLock.acquire();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy()");
        notificationMgr.cancelAll();
        Log.v(TAG, "releasing wake lock");
        wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public int getResponseCode() {
        return 0;
    }

    public class ChatServiceBinder extends Binder {
        public ChatService getService() {
            return ChatService.this;
        }
    }


    private void handleData(Bundle data) {
        int command = data.getInt(CMD);
        Log.d(TAG, "-(<- received command data to service: command=" + command);
        if (command == CMD_JOIN_CHAT) {
            notificationDecorator.displaySimpleNotification("Joining Chat...", "Connecting as User: " + myName);
            sendBroadcastConnected();
            sendBroadcastUserJoined(myName, 1);
            setTimer();
        } else if (command == CMD_LEAVE_CHAT) {
            notificationDecorator.displaySimpleNotification("Leaving Chat...", "Disconnecting");
            sendBroadcastUserLeft(myName, 0);
            sendBroadcastNotConnected();
            if (timer != null) timer.cancel();
            stopSelf();
        } else if (command == CMD_SEND_MESSAGE) {
            String messageText = (String) data.get(KEY_MESSAGE_TEXT);
            notificationDecorator.displayExpandableNotification("Sending message...", messageText);
            chatMessageStore.insert(new ChatMessage(myName, messageText));
            sendBroadcastNewMessage(myName, messageText);
            setTimer();
        } else if (command == CMD_RECEIVE_MESSAGE) {
            String testUser = "Test User";
            String testMessage = "Simulated Message";
            notificationDecorator.displayExpandableNotification("New message...: "+ testUser, testMessage);
            chatMessageStore.insert(new ChatMessage(testUser, testMessage));
            sendBroadcastNewMessage(testUser, testMessage);
        } else {
            Log.w(TAG, "Ignoring Unknown Command! id=" + command);
        }
    }

    private void loadUserNameFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        myName = prefs.getString(Constants.PREF_KEY_USER_NAME, "Default Name");
    }

    private void loadTimerFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String defaultTime = getResources().getString(R.string.pref_default_timeout);
        String PREF_KEY_TIMEOUT = getResources().getString(R.string.pref_key_timeout);
        String timepref = prefs.getString( PREF_KEY_TIMEOUT, defaultTime);
        time = Long.valueOf( timepref );
        Log.d(TAG, "loaded timeout preference: " + time);
    }


    // broadcasts
    private void sendBroadcastNotConnected() {
        Log.d(TAG, "->(+)<- sending broadcast: BROADCAST_SERVER_NOT_CONNECTED");
        Intent intent = new Intent();
        intent.setAction(Constants.BROADCAST_SERVER_NOT_CONNECTED);
        sendBroadcast(intent);
    }

    private void sendBroadcastConnected() {
        Log.d(TAG, "->(+)<- sending broadcast: BROADCAST_SERVER_CONNECTED");
        Intent intent = new Intent();
        intent.setAction(Constants.BROADCAST_SERVER_CONNECTED);

        sendBroadcast(intent);
    }

    private void sendBroadcastUserJoined(String userName, int userCount) {
        Log.d(TAG, "->(+)<- sending broadcast: BROADCAST_USER_JOINED");
        Intent intent = new Intent();
        intent.setAction(Constants.BROADCAST_USER_JOINED);

        Bundle data = new Bundle();
        data.putInt(Constants.CHAT_USER_COUNT, userCount);
        data.putString(Constants.CHAT_USER_NAME, userName);
        intent.putExtras(data);

        sendBroadcast(intent);
    }

    private void sendBroadcastTimeout(String timeoutMessage) {
        Log.d(TAG, "->(+)<- sending broadcast: BROADCAST_SESSION_TIMEOUT");
        Intent intent = new Intent();
        intent.setAction(Constants.BROADCAST_SESSION_TIMEOUT);

        Bundle data = new Bundle();
        data.putString(Constants.TIMEOUT_MESSAGE, timeoutMessage);
        intent.putExtras(data);

        sendBroadcast(intent);
    }

    private void sendBroadcastUserLeft(String userName, int userCount) {
        Log.d(TAG, "->(+)<- sending broadcast: BROADCAST_USER_LEFT");
        Intent intent = new Intent();
        intent.setAction(Constants.BROADCAST_USER_LEFT);

        Bundle data = new Bundle();
        data.putInt(Constants.CHAT_USER_COUNT, userCount);
        data.putString(Constants.CHAT_USER_NAME, userName);
        intent.putExtras(data);

        sendBroadcast(intent);
    }

    private void sendBroadcastNewMessage(String userName, String message) {
        Log.d(TAG, "->(+)<- sending broadcast: BROADCAST_NEW_MESSAGE");
        Intent intent = new Intent();
        intent.setAction(Constants.BROADCAST_NEW_MESSAGE);

        Bundle data = new Bundle();
        data.putString(Constants.CHAT_MESSAGE, message);
        data.putString(Constants.CHAT_USER_NAME, userName);
        intent.putExtras(data);

        sendBroadcast(intent);
    }

    private void sendBroadcastUserTyping(String userName) {
        Log.d(TAG, "->(+)<- sending broadcast: BROADCAST_USER_TYPING");
        Intent intent = new Intent();
        intent.setAction(Constants.BROADCAST_USER_TYPING);
        sendBroadcast(intent);
    }

    private void setTimer() {
        long MILISECONDS_IN_A_SECOND = 1000;
        long MILISECONDS_IN_A_MINUTE = 60 * MILISECONDS_IN_A_SECOND;
        long timeout = time * MILISECONDS_IN_A_MINUTE;

        currentSessionTime = 0;
        Log.d(TAG, "setTimer() time=" + time + " timeout=" + timeout);

        final String timeoutMessage = "Session closed after reaching the limit: "
                + time + " min.";

        if (timer != null) timer.cancel();
        timer = new CountDownTimer(timeout, MILISECONDS_IN_A_SECOND) {

            public void onTick(long millisUntilFinished) {
                currentSessionTime++;
                Log.d(TAG, "currentSessionTime: " + currentSessionTime + "s");
            }

            public void onFinish() {
                Log.d(TAG, "timer finished");
                sendBroadcastTimeout(timeoutMessage);
                leaveChat();
            }
        };
        timer = timer.start();
    }

    private void leaveChat(){
        Bundle data = new Bundle();
        data.putInt(ChatService.CMD, ChatService.CMD_LEAVE_CHAT);
        Intent intent = new Intent(this, ChatService.class);
        intent.putExtras(data);
        startService(intent);
    }
}
