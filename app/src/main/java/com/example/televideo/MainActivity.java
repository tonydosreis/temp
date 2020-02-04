package com.example.televideo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.rtsp.RtspClient;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements
        OnClickListener, RtspClient.Callback,
        Session.Callback,
        SurfaceHolder.Callback,
        OnCheckedChangeListener {

    public final static String TAG = "MainActivity";

    private FrameLayout mLayoutSurfaceView;
    private FrameLayout mLayoutStartScreen;
    private FrameLayout mLayoutSettings;
    private Button mButtonStartScreen;
    private Button mButtonSave;
    private Button mButtonVideo;
    private ImageButton mButtonStart;
    private ImageButton mButtonFlash;
    private ImageButton mButtonCamera;
    private ImageButton mButtonSettings;
    private RadioGroup mRadioGroup;
    private FrameLayout mLayoutVideoSettings;
    private FrameLayout mLayoutServerSettings;
    private SurfaceView mSurfaceView;
    private TextView mTextBitrate;
    private EditText mEditTextURI;
    private EditText mEditTextPassword;
    private EditText mEditTextUsername;
    private ProgressBar mProgressBar;
    private Session mSession;
    private RtspClient mClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Takes care of permissions
        permissions();

        //Prevents the screen from turning off
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Does not show the app name on the top of the screen as it would normally
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Sets the layout of the screen
        setContentView(R.layout.main);

        //Links the variables to the views
        mLayoutSurfaceView = (FrameLayout) findViewById(R.id.surface_layout);
        mLayoutStartScreen = (FrameLayout) findViewById(R.id.start_screen);
        mLayoutSettings = (FrameLayout) findViewById(R.id.setting_layout);
        mButtonStartScreen = (Button) findViewById(R.id.start_screen_button);
        mButtonVideo = (Button) findViewById(R.id.video);
        mButtonSave = (Button) findViewById(R.id.save);
        mButtonStart = (ImageButton) findViewById(R.id.start);
        mButtonFlash = (ImageButton) findViewById(R.id.flash);
        mButtonCamera = (ImageButton) findViewById(R.id.camera);
        mButtonSettings = (ImageButton) findViewById(R.id.settings);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface);
        mEditTextURI = (EditText) findViewById(R.id.uri);
        mEditTextUsername = (EditText) findViewById(R.id.username);
        mEditTextPassword = (EditText) findViewById(R.id.password);
        mTextBitrate = (TextView) findViewById(R.id.bitrate);
        mLayoutVideoSettings = (FrameLayout) findViewById(R.id.video_layout);
        mLayoutServerSettings = (FrameLayout) findViewById(R.id.server_layout);
        mRadioGroup =  (RadioGroup) findViewById(R.id.radio);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);

        //Sets event listeners for these views, the function that is called on event is defined later in the onCLick method
        mButtonStartScreen.setOnClickListener(this);
        mRadioGroup.setOnCheckedChangeListener(this);
        mRadioGroup.setOnClickListener(this);
        mButtonStart.setOnClickListener(this);
        mButtonSave.setOnClickListener(this);
        mButtonFlash.setOnClickListener(this);
        mButtonCamera.setOnClickListener(this);
        mButtonVideo.setOnClickListener(this);
        mButtonSettings.setOnClickListener(this);
    }

    public void begin()
    {
        //gets rid of start button
        mLayoutStartScreen.setVisibility(View.GONE);

        //Makes other views visible
        mLayoutServerSettings.setVisibility(View.VISIBLE);
        mTextBitrate.setVisibility(View.VISIBLE);
        mLayoutSurfaceView.setVisibility(View.VISIBLE);
        mLayoutSettings.setVisibility(View.VISIBLE);

        //sets the tag of this view as off
        mButtonFlash.setTag("off");

        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        //If there is a uri in mPrefs, makes server input settings layout disappear
        if (mPrefs.getString("uri", null) != null) mLayoutServerSettings.setVisibility(View.GONE);

        //sets the editTexts to what is stored in mPrefs or the default values
        mEditTextURI.setText(mPrefs.getString("uri", getString(R.string.default_stream)));
        mEditTextPassword.setText(mPrefs.getString("password", ""));
        mEditTextUsername.setText(mPrefs.getString("username", ""));

        //libstreaming: Fundamental for the streaming
        // Configures the SessionBuilder
        mSession = SessionBuilder.getInstance()
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                .setAudioQuality(new AudioQuality(8000,16000))
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setSurfaceView((net.majorkernelpanic.streaming.gl.SurfaceView) mSurfaceView)
                .setPreviewOrientation(0)
                .setCallback(this)
                .build();

        // Configures the RTSP client
        mClient = new RtspClient();
        mClient.setSession(mSession);
        mClient.setCallback(this);

        // Use this to force streaming with the MediaRecorder API
        //mSession.getVideoTrack().setStreamingMethod(MediaStream.MODE_MEDIARECORDER_API);

        // Use this to stream over TCP, EXPERIMENTAL!
        //mClient.setTransportMode(RtspClient.TRANSPORT_TCP);

        // Use this if you want the aspect ratio of the surface view to
        // respect the aspect ratio of the camera preview
        // This fixes the image distortion, but the preview doesn't occupy the whole screen
        //mSurfaceView.setAspectRatioMode(SurfaceView.ASPECT_RATIO_PREVIEW);

        mSurfaceView.getHolder().addCallback(this);

        selectQuality();
    }

    //Responds to event of checked radio button change
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        mLayoutVideoSettings.setVisibility(View.GONE);
        mLayoutServerSettings.setVisibility(View.VISIBLE);
        selectQuality();
    }

    //Responds to event of any button click
    @Override
    public void onClick(View v) {
        //Does something different depending on the button that was pressed
        switch (v.getId()) {
            case R.id.start_screen_button:
                begin();
                break;
            case R.id.start:
                mLayoutServerSettings.setVisibility(View.GONE);
                toggleStream();
                break;
            case R.id.flash:
                if (mButtonFlash.getTag().equals("on")) {
                    mButtonFlash.setTag("off");
                    mButtonFlash.setImageResource(R.drawable.ic_flash_on_holo_light);
                } else {
                    mButtonFlash.setImageResource(R.drawable.ic_flash_off_holo_light);
                    mButtonFlash.setTag("on");
                }
                mSession.toggleFlash();
                break;
            case R.id.camera:
                mSession.switchCamera();
                break;
            case R.id.settings:
                if (mLayoutVideoSettings.getVisibility() == View.GONE &&
                        mLayoutServerSettings.getVisibility() == View.GONE) {
                    mLayoutServerSettings.setVisibility(View.VISIBLE);
                } else {
                    mLayoutServerSettings.setVisibility(View.GONE);
                    mLayoutVideoSettings.setVisibility(View.GONE);
                }
                break;
            case R.id.video:
                mRadioGroup.clearCheck();
                mLayoutServerSettings.setVisibility(View.GONE);
                mLayoutVideoSettings.setVisibility(View.VISIBLE);
                break;
            case R.id.save:
                mLayoutServerSettings.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mClient.release();
        mSession.release();
        mSurfaceView.getHolder().removeCallback(this);
    }

    //Executes change in video quality, always called when the radio buttons of video qualities are changed
    private void selectQuality() {

        //Gets radioButton that is currently checked
        int id = mRadioGroup.getCheckedRadioButtonId();
        RadioButton button = (RadioButton) findViewById(id);
        if (button == null) return;

        //Gets the text from the radioButton
        String text = button.getText().toString();

        //Extracts specific information from the text
        Pattern pattern = Pattern.compile("(\\d+)x(\\d+)\\D+(\\d+)\\D+(\\d+)");
        Matcher matcher = pattern.matcher(text);
        matcher.find();
        int width = Integer.parseInt(matcher.group(1));
        int height = Integer.parseInt(matcher.group(2));
        int framerate = Integer.parseInt(matcher.group(3));
        int bitrate = Integer.parseInt(matcher.group(4))*1000;

        //Updates video Quality in the session object
        mSession.setVideoQuality(new VideoQuality(width, height, framerate, bitrate));

        //Toasts the current video settings
        Toast.makeText(this, ((RadioButton)findViewById(id)).getText(), Toast.LENGTH_SHORT).show();

        //Logs the current video settings
        Log.d(TAG, "Selected resolution: "+width+"x"+height);
    }

    //Sets these buttons to the enabled state
    //When is this used? Why is it necessary?
    private void enableUI() {
        mButtonStart.setEnabled(true);
        mButtonCamera.setEnabled(true);
    }

    // Connects/disconnects to the RTSP server and starts/stops the stream
    //Apparently starts streaming if it in not and stops if it is
    public void toggleStream() {
        mProgressBar.setVisibility(View.VISIBLE);
        //if is not streaming
        if (!mClient.isStreaming()) {
            String ip,port,path;

            // We save the content user inputs in Shared Preferences
            SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            Editor editor = mPrefs.edit();
            editor.putString("uri", mEditTextURI.getText().toString());
            editor.putString("password", mEditTextPassword.getText().toString());
            editor.putString("username", mEditTextUsername.getText().toString());
            editor.commit();

            // We parse the URI written in the Editext
            Pattern uri = Pattern.compile("rtsp://(.+):(\\d*)/(.+)");
            Matcher m = uri.matcher(mEditTextURI.getText()); m.find();
            ip = m.group(1);
            port = m.group(2);
            path = m.group(3);

            //Sets parameters of mClient and starts Streaming
            mClient.setCredentials(mEditTextUsername.getText().toString(), mEditTextPassword.getText().toString());
            mClient.setServerAddress(ip, Integer.parseInt(port));
            mClient.setStreamPath("/"+path);
            mClient.startStream();

            //if is streaming
        } else {
            // Stops the stream and disconnects from the RTSP server
            mClient.stopStream();
        }
    }

    //I do not understand this, especially the AlertDialog.Builder part
    private void logError(final String msg) {
        final String error = (msg == null) ? "Error unknown" : msg;
        // Displays a popup to report the eror to the user
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(msg).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {}
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    //Implementation of this method that exists in Session.Callback
    //Updates textView with bitrate
    @Override
    public void onBitrateUpdate(long bitrate) {
        mTextBitrate.setText(""+bitrate/1000+" kbps");
    }

    //Implementation of this method that exists in Session.Callback
    @Override
    public void onPreviewStarted() {

        //getCamera defined in the VideoStream class, which is defined in the libStreaming library
        //returns the id of the camera currently selected

        //Current camera is facing forward
        if (mSession.getCamera() == CameraInfo.CAMERA_FACING_FRONT) {
            mButtonFlash.setEnabled(false);
            mButtonFlash.setTag("off");
            //Changes the icon of the flash button
            mButtonFlash.setImageResource(R.drawable.ic_flash_on_holo_light);
        }
        //Current camera is facing backwards
        else {
            mButtonFlash.setEnabled(true);
        }
    }

    //Implementation of this method that exists in the interface Session.Callback
    //Does nothing as far as I know
    @Override
    public void onSessionConfigured() {
    }

    //Implementation of this method that exists in the interface Session.Callback
    @Override
    public void onSessionStarted() {
        //Enables some buttons
        enableUI();
        //Changes the icon of the record button
        mButtonStart.setImageResource(R.drawable.ic_switch_video_active);
        mProgressBar.setVisibility(View.GONE);
    }

    //Implementation of this method that exists in the interface Session.Callback
    @Override
    public void onSessionStopped() {
        //Enables some buttons
        enableUI();
        //Changes the icon of the record button
        mButtonStart.setImageResource(R.drawable.ic_switch_video);
        mProgressBar.setVisibility(View.GONE);
    }

    //Implementation of this method that exists in the interface Session.Callback
    //Some error with the session occurred
    @Override
    public void onSessionError(int reason, int streamType, Exception e) {
        mProgressBar.setVisibility(View.GONE);
        //Different errors and corresponding actions
        switch (reason) {
            case Session.ERROR_CAMERA_ALREADY_IN_USE:
                break;
            case Session.ERROR_CAMERA_HAS_NO_FLASH:
                mButtonFlash.setImageResource(R.drawable.ic_flash_on_holo_light);
                mButtonFlash.setTag("off");
                break;
            case Session.ERROR_INVALID_SURFACE:
                break;
            case Session.ERROR_STORAGE_NOT_READY:
                break;
            case Session.ERROR_CONFIGURATION_NOT_SUPPORTED:
                VideoQuality quality = mSession.getVideoTrack().getVideoQuality();
                logError("The following settings are not supported on this phone: "+
                        quality.toString()+" "+
                        "("+e.getMessage()+")");
                e.printStackTrace();
                return;
            case Session.ERROR_OTHER:
                break;
        }

        if (e != null) {
            logError(e.getMessage());
            e.printStackTrace();
        }
    }

    //Implementation of this method that exists in the interface RtspClient.Callback (the only method in this interface)
    //I don't really know when or why this method is called
    @Override
    public void onRtspUpdate(int message, Exception e) {
        switch (message) {
            case RtspClient.ERROR_CONNECTION_FAILED:
            case RtspClient.ERROR_WRONG_CREDENTIALS:
                mProgressBar.setVisibility(View.GONE);
                enableUI();
                logError(e.getMessage());
                e.printStackTrace();
                break;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSession.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mClient.stopStream();
    }

    //Takes care of permissions
    public void permissions()
    {
        //Request code that is sent to the onRequestPermissionsResult callback
        final int MULTIPLE_PERMISSIONS_CODE = 1;

        //List of permissions that are needed
        //Created to make it possible to use the for loop later
        ArrayList<String> permissionsList = new ArrayList<>();
        permissionsList.add(Manifest.permission.CAMERA);
        permissionsList.add(Manifest.permission.RECORD_AUDIO);
        permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        //List of permissions to ask to the user
        ArrayList<String> permissionsToAsk = new ArrayList<>();

        //goes through every dangerous permission that is necessary
        for(String permission : permissionsList)
        {
            //Checks if permission is denied
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED)
            {
                //Adds permission to list of permissions to ask
                permissionsToAsk.add(permission);
            }
        }

        //If there are permissions to ask
        if(!permissionsToAsk.isEmpty())
        {
            //Asks for permissions
            ActivityCompat.requestPermissions(this, permissionsToAsk.toArray(new String[permissionsToAsk.size()]), MULTIPLE_PERMISSIONS_CODE );
            //onRequestPermissionsResult callback is called after the user makes a choice
            //It must be implemented in order to do something
        }
    }
}