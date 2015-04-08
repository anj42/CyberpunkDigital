package com.example.antoni.cyberpunk_digital;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.Wearable;



import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class CyberPunkDigital extends CanvasWatchFaceService {

    /** used for logging the messages, each time the message will be associated with "BlackAndWhiteWatchface" **/
    private static final String TAG = "CyberpunkDigital";

    /** custom font **/
    private static Typeface FUTURE_TYPEFACE = null;

    @Override
    public Engine onCreateEngine() { return new Engine(); }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener, GoogleApiClient.OnConnectionFailedListener {

        static final int MSG_UPDATE_TIME = 0;

        /** alpha values for interactive and mute mode (will reduce the opacity) **/
        static final int NORMAL_ALPHA= 255;
        static final int MUTE_ALPHA = 100;

        /** handler to update time periodically in interactive mode **/
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage (Message message){
            switch (message.what){
                case MSG_UPDATE_TIME:
                    if(Log.isLoggable(TAG, Log.VERBOSE)){
                        Log.v(TAG, "updating time");
                    }
                    /** invalidate() forces the view to draw **/
                    invalidate();
                    break;
                }
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(CyberPunkDigital.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();


        /**change the time zone if neccessary **/
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra ("time-zone"));
                mTime.setToNow();
            }
        };

        /** declarations of all the needed variables and components **/
        boolean mRegisteredTimeZoneReceiver = false;

        // responsible for the graphics
        Paint mBackgroundPaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Bitmap mBackgroundBitmap;
        Bitmap mBackgroundScaledBitmap;

        //display colours
        int mInteractiveHourDigitsColor= Color.parseColor("#d4961f");
        int mInteractiveMinuteDigitsColor= Color.parseColor("#d4961f");
        int mAmbientHourDigitsColor= Color.GRAY;
        int mAmbientMinutesDigitsColor= Color.GRAY;

        //responsible for the logical operations etc
        boolean mMute;
        Time mTime;

        /** if the display supports fewer bits in ambient mode, this boolean should be of value "true" **/
        boolean mLowBitAmbient;


        /** what happens when the watchface gets created **/
        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)){
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            FUTURE_TYPEFACE = Typeface.createFromAsset(getAssets(), "fonts/future.ttf");


            /** decide the style of the watchface- what it will be able to do, peek modes etc **/
            setWatchFaceStyle(new WatchFaceStyle.Builder(CyberPunkDigital.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)                             // peek cards enabled
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)     // the background can be interruptive
                    .setShowSystemUiTime(false)                                                     // not needed, the watchface has its own "time"
                    .build());
            Resources resources = CyberPunkDigital.this.getResources();

            /** create and set colours to elements on the display **/
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.digital_background);
            mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
            mHourPaint = createTextPaint(mInteractiveHourDigitsColor, FUTURE_TYPEFACE);
            mMinutePaint = createTextPaint(mInteractiveMinuteDigitsColor, FUTURE_TYPEFACE);

            mTime = new Time();
        }

        /** what will happen when the watchface is turned off **/
        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        /** create a text paint **/
        private Paint createTextPaint (int defaultInteractiveColor, Typeface typeface){
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);                                                // set the color
            paint.setTypeface(typeface);                                                            // set the typeface (bold in this example)
            paint.setAntiAlias(true);                                                               // "false" in ambient mode
            return paint;
        }

        /** actions for the watchface when it's visible **/
        @Override
        public void onVisibilityChanged (boolean visible) {
            if(Log.isLoggable(TAG, Log.DEBUG)){
                Log.d(TAG, "onVisibilityChanged:" + visible);
            }
            super.onVisibilityChanged(visible);

            if (visible == true) {
                registerReceiver();

                //update time zone if needed
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            }
            else {
                unregisterReceiver();

                if(mGoogleApiClient != null && mGoogleApiClient.isConnected()){
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }
            updateTimer();
        }

        /** register the receiver through this method **/
        private void registerReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            CyberPunkDigital.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        /** unregister the receiver when not needed **/
        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver){
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            CyberPunkDigital.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /** method used to fit the display into square and round devices **/
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onApplyWindowInsets (WindowInsets insets){
            if (Log.isLoggable(TAG, Log.DEBUG)){
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));      // log will be different for square and round devices
            }
            super.onApplyWindowInsets(insets);

            //Load resources with alternate values for round and square devices
            Resources resources = CyberPunkDigital.this.getResources();
            boolean isRound = insets.isRound();

            float textSize = resources.getDimension(isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
        }

        /** used for burn-in protection or low-bit ambient mode, varies on different devices **/
        @Override
        public void onPropertiesChanged (Bundle properties){
            super.onPropertiesChanged(properties);

            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHourPaint.setTypeface(burnInProtection ? FUTURE_TYPEFACE : FUTURE_TYPEFACE );             // if burn in protection is on the hour digits will be in normal typeface

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

            if (Log.isLoggable(TAG, Log.DEBUG)){
                Log.d(TAG, "onPropertiesChanged : burn-in protection = " + burnInProtection + ", low-bit ambient = " + mLowBitAmbient);
            }
        }

        /** forces the graphics to be drawn on time tick **/
        @Override
        public void onTimeTick(){
            super.onTimeTick();
            if (Log.isLoggable(TAG, Log.DEBUG)){
                Log.d(TAG, "onTimeTick : ambient = " + isInAmbientMode());
            }
            invalidate();
        }

        /** this method will change the properties of the display when it enters ambient mode (gray colour digits, black background etc.) **/
        @Override
        public void onAmbientModeChanged (boolean inAmbientMode){
            super.onAmbientModeChanged(inAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)){
                Log.d(TAG, "onAmbientModeChanged: " +inAmbientMode);
            }

            adjustPaintColorToCurrentMode (mHourPaint,mInteractiveHourDigitsColor, mAmbientHourDigitsColor);
            adjustPaintColorToCurrentMode (mMinutePaint, mInteractiveMinuteDigitsColor, mAmbientMinutesDigitsColor);

            // this will turn off the anti aliasing, not needed in ambient mode
            if(mLowBitAmbient) {
                boolean antiAlias= !inAmbientMode;                                                  // takes the opposite of inAmbientMode
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
            }
            invalidate();
            updateTimer();

        }

        // changes the colour based on the mode the device is in (ambient/interactive)
        private void adjustPaintColorToCurrentMode (Paint paint, int interactiveColor, int ambientColor){
            paint.setColor(isInAmbientMode()? ambientColor : interactiveColor);
        }

        @Override
        public void onInterruptionFilterChanged (int interruptionFilter){
            if(Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onInterruptionFilterChanged: + " + interruptionFilter);
            }
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;

            // will change the alpha values to reduce opacity in mute mode
            if(mMute != inMuteMode){
                mMute= inMuteMode;
                int alpha= inMuteMode ? MUTE_ALPHA : NORMAL_ALPHA;
                mHourPaint.setAlpha(alpha);
                mMinutePaint.setAlpha(alpha);
                invalidate();
            }
        }


        private String formatTwoDigitNumber (int hour) {
            return String.format("%02d", hour);
        }


        /** what will happen when the display is drawn **/
        @Override
        public  void  onDraw (Canvas canvas, Rect bounds) {

            mTime.setToNow();

            int width = bounds.width();
            int height = bounds.height();

            // Draw the background, scaled to fit.
            if (isInAmbientMode() == false) {
                if (mBackgroundScaledBitmap == null
                        || mBackgroundScaledBitmap.getWidth() != width
                        || mBackgroundScaledBitmap.getHeight() != height) {
                    mBackgroundScaledBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                            width, height, true /* filter */);
                }
                canvas.drawBitmap(mBackgroundScaledBitmap, 0, 0, null);                                           //create new canvas with scaled bitmap
            }
            else if (isInAmbientMode() == true) {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            float centerX = width / 2f;
            float centerY = height / 2f;

            String hourString =formatTwoDigitNumber(mTime.hour);
            float positionXHrs = centerX - (mHourPaint.measureText(hourString)/2) -65;
            canvas.drawText(hourString, positionXHrs, (centerY -13), mHourPaint);


            String minuteString = formatTwoDigitNumber(mTime.minute);
            float positionXMin = centerX + 65 - (mMinutePaint.measureText(minuteString)/2)  ;
            canvas.drawText(minuteString, positionXMin, (centerY + -13), mMinutePaint);

        }

         /** starts the mUpdateTimeHandler if it should be running or stops when it is running **/
        private void updateTimer () {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /** determines whether the timer should be running or not **/
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            //updateConfigDataItemAndUiOnStartup();
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {

        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {

        }
    }
}98
