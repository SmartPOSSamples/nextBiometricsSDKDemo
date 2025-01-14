package com.nextbiometrics.samples;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.*;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;

/*API-Level-30-Start*/
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
/*API-Level-30-End*/

/*
 * API-Level-29 and below
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
*/

import com.nextbiometrics.biometrics.NBBiometricsContext;
import com.nextbiometrics.biometrics.NBBiometricsExtractResult;
import com.nextbiometrics.biometrics.NBBiometricsFingerPosition;
import com.nextbiometrics.biometrics.NBBiometricsIdentifyResult;
import com.nextbiometrics.biometrics.NBBiometricsSecurityLevel;
import com.nextbiometrics.biometrics.NBBiometricsStatus;
import com.nextbiometrics.biometrics.NBBiometricsTemplate;
import com.nextbiometrics.biometrics.NBBiometricsTemplateType;
import com.nextbiometrics.biometrics.NBBiometricsVerifyResult;
import com.nextbiometrics.devices.NBDevice;
import com.nextbiometrics.devices.NBDeviceScanResult;
import com.nextbiometrics.devices.NBDeviceScanStatus;
import com.nextbiometrics.devices.NBDeviceScanFormatInfo;
import com.nextbiometrics.devices.NBDeviceSecurityModel;
import com.nextbiometrics.devices.NBDeviceType;
import com.nextbiometrics.devices.NBDevices;
import com.nextbiometrics.system.NextBiometricsException;
import com.nextbiometrics.biometrics.event.*;
import java.io.*;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.Thread;

import static java.lang.System.currentTimeMillis;

public class MainActivity extends Activity {
    private static final String DEFAULT_SPI_NAME = "/dev/spidev4.1";
    //Default SYSFS path to access GPIO pins 	
    private static final String DEFAULT_SYSFS_PATH = "/sys/class/gpio";
    private int DEFAULT_SPICLK = 8000000;
    private int DEFAULT_AWAKE_PIN_NUMBER = 704;
    private int DEFAULT_RESET_PIN_NUMBER = 703;
    private int DEFAULT_CHIP_SELECT_PIN_NUMBER = 705;
    public static int FLAGS;
    private static Bitmap templateBmp;
    private static byte[] bufferBytes;

    // UI
    Button btnScanAndExtract = null;
    ImageView imageView = null;

    // Info used by non-UI threads
    NBDevice device = null; //device handle
    boolean isSpi = false;
    NBDeviceScanFormatInfo scanFormatInfo = null;
    NBDeviceScanResult bgscanResult = null;
    boolean terminate = false;      // Do we need to call NBDevices.terminate()?
    long timeStart = 0;
    long timeStop = 0;
    int quality = 0;
    boolean success = false;

    // For synchronization with DialogInterface
    boolean dialogResult;
    // For displaying messages
    TextView lastMessage = null;
    TextView textResults = null;
    PreviewListener previewListener = new PreviewListener();

    //Workaround Solution for SPI When IO Command Failed
    String retrySPICause = "Command failed";

    // Enable and Antispoof Support
    private static final int CONFIGURE_ANTISPOOF = 108;
    private static final int CONFIGURE_ANTISPOOF_THRESHOLD = 109;
    private static final int ENABLE_ANTISPOOF = 1;
    private static final int DISABLE_ANTISPOOF = 0;
    private static final int MIN_ANTISPOOF_THRESHOLD = 0;
    private static final int MAX_ANTISPOOF_THRESHOLD = 1000;
    private int spoofScore = MAX_ANTISPOOF_THRESHOLD;
    private static final String DEFAULT_ANTISPOOF_THRESHOLD = "363";
    private static String ANTISPOOF_THRESHOLD = DEFAULT_ANTISPOOF_THRESHOLD;
    private static boolean isSpoofEnabled = false;
    private static boolean isValidSpoofScore = false;
    private static boolean isScanAndExtractInProgress = false;
    private static boolean isAutoSaveEnabled = false;
    private static final String spoofCause = "Spoof Detected.";
    private static int spoofThreshold = 0;
    public static long previewStartTime = 0;
    public static long previewEndTime = 0;
    public static int imageFormatSpinnerSelected = 0;
    private static final int ENABLE_BACKGROUND_SUBTRACTION = 1;
    private static final int DISABLE_BACKGROUND_SUBTRACTION = 0;


    //Storage Permission request code
    private static final int READ_WRITE_PERMISSION_REQUEST_CODE = 1;
    private static final int PERMISSION_CALLBACK_CODE = 2;
    public static boolean isPermissionGranted = false;
    private static String imageFormatName;
    
    // Timer for closing the app when user pressed back button.
    private long pressedTime;

    //
    // Preview Listener
    //
    class PreviewListener implements NBBiometricsScanPreviewListener {
        private int counter = 0;
        private int sequence = 0;
        private byte[] lastImage = null;
        private long timeFDET = 0;
        private long timeScanStart = 0;
        private long timeScanEnd = 0;
        private long timeOK = 0;
        private int fdetScore = 0;

        public void reset() {
            showMessage("");        // Placeholder for preview
            counter = 0;
            sequence++;
            lastImage = null;
            timeFDET = timeOK = timeScanEnd = timeScanStart = 0;
            fdetScore = 0;
            spoofScore = MAX_ANTISPOOF_THRESHOLD;
        }

        public byte[] getLastImage() {
            return lastImage;
        }

        public long getTimeOK() {
            return timeOK;
        }

        public long getTimeScanStart() {
            return timeScanStart;
        }

        public long getTimeScanEnd() {
            return timeScanEnd;
        }

        public long getTimeFDET() {
            return timeFDET;
        }

        public int getFdetScore() {
            return fdetScore;
        }

        public int getSpoofScore() {
            return spoofScore;
        }

        @Override
        public void preview(NBBiometricsScanPreviewEvent event) {
            byte[] image = event.getImage();
            spoofScore = event.getSpoofScoreValue();

            isValidSpoofScore = true;

            if(spoofScore <= MIN_ANTISPOOF_THRESHOLD || spoofScore > MAX_ANTISPOOF_THRESHOLD) {
                spoofScore = MIN_ANTISPOOF_THRESHOLD;
                isValidSpoofScore = false;
            }
            if(isValidSpoofScore)
            {
                updateMessage(String.format("PREVIEW #%d: Status: %s, Finger detect score: %d, Spoof Score: %d, image %d bytes",
                    ++counter, event.getScanStatus().toString(), event.getFingerDetectValue(), spoofScore, image == null ? 0 : image.length));
            }
            else
            {
                updateMessage(String.format("PREVIEW #%d: Status: %s, Finger detect score: %d, image %d bytes",
                    ++counter, event.getScanStatus().toString(), event.getFingerDetectValue(), image == null ? 0 : image.length));
            }
            if (image != null) lastImage = image;
            // Approx. time when finger was detected = last preview before operation that works with finger image
            if (event.getScanStatus() != NBDeviceScanStatus.BAD_QUALITY &&
                event.getScanStatus() != NBDeviceScanStatus.BAD_SIZE &&
                event.getScanStatus() != NBDeviceScanStatus.DONE &&
                event.getScanStatus() != NBDeviceScanStatus.OK &&
                event.getScanStatus() != NBDeviceScanStatus.KEEP_FINGER_ON_SENSOR &&
                event.getScanStatus() != NBDeviceScanStatus.SPOOF &&
                event.getScanStatus() != NBDeviceScanStatus.SPOOF_DETECTED &&
                event.getScanStatus() != NBDeviceScanStatus.WAIT_FOR_DATA_PROCESSING &&
                event.getScanStatus() != NBDeviceScanStatus.CANCELED) {
                timeFDET = currentTimeMillis();
            }
            if (event.getScanStatus() == NBDeviceScanStatus.DONE ||
                event.getScanStatus() == NBDeviceScanStatus.OK ||
                event.getScanStatus() == NBDeviceScanStatus.CANCELED ||
                event.getScanStatus() == NBDeviceScanStatus.WAIT_FOR_DATA_PROCESSING ||
                event.getScanStatus() == NBDeviceScanStatus.SPOOF_DETECTED) {
                // Approx. time when scan was finished = time of the first event OK, DONE, CANCELED or WAIT_FOR_DATA_PROCESSING
                if (timeScanEnd == 0) timeScanEnd = currentTimeMillis();
            }
            else {
                // Last scan start = time of any event just before OK, DONE, CANCELED or WAIT_FOR_DATA_PROCESSING
                timeScanStart = currentTimeMillis();
            }
            // Time when scan was completed
            if (event.getScanStatus() == NBDeviceScanStatus.OK || event.getScanStatus() == NBDeviceScanStatus.DONE || event.getScanStatus() == NBDeviceScanStatus.CANCELED || event.getScanStatus() == NBDeviceScanStatus.SPOOF_DETECTED) {
                timeOK = currentTimeMillis();
                fdetScore = event.getFingerDetectValue();
            }

            if(previewListener.getLastImage() != null && (device.toString().contains("65210") || device.toString().contains("65200"))) {
                if(previewStartTime == 0)
                {
                    previewStartTime = currentTimeMillis();
                }

                previewEndTime = currentTimeMillis();
                showResultOnUiThread(previewListener.getLastImage(),
                        String.format("Preview scan time = %d msec,\n Finger detect score = %d", previewEndTime - previewStartTime, event.getFingerDetectValue()));
                previewStartTime = currentTimeMillis();
            }
        }
    };
    
    //
    // Overriden methods
    //
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		
        //Use the below implementation while using SYSFS interface
        final int PIN_OFFSET = 902;
//        DEFAULT_AWAKE_PIN_NUMBER = PIN_OFFSET + 69;
//        DEFAULT_RESET_PIN_NUMBER = PIN_OFFSET + 12;
//        DEFAULT_CHIP_SELECT_PIN_NUMBER = PIN_OFFSET + 18;
        FLAGS = NBDevice.DEVICE_CONNECT_TO_SPI_SKIP_GPIO_INIT_FLAG;

        /* Use the below implementation while using /dev/gpiochip interface,
           pass the chip number and gpio number to makePin methode */
        /*DEFAULT_AWAKE_PIN_NUMBER = makePin(0, 69);
        DEFAULT_RESET_PIN_NUMBER = makePin(0, 12);
        DEFAULT_CHIP_SELECT_PIN_NUMBER = makePin(0, 18);
        FLAGS = 0;*/
		
        textResults = (TextView)findViewById(R.id.textResults);
        imageView = (ImageView)findViewById(R.id.imageView);
        btnScanAndExtract = (Button)findViewById(R.id.scanAndExtract);
        btnScanAndExtract.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                Thread processingThread = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        // Increase thread priority -- especially SPI fingerprint sensors require this, otherwise the image readout is slow
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                        scanAndExtract();
                    }
                });

                btnScanAndExtract.setEnabled(false);    // Prevent further clicks until the operation is completed
                clearMessages();
                imageView.setImageResource(R.drawable.app_icon);
                textResults.setText("");
                processingThread.start();
            }
        });

        Thread initializationThread = new Thread(new Runnable() {

            @Override
            public void run() {
                // Increase thread priority -- especially SPI fingerprint sensors require this, otherwise the image readout is slow
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

                //Request permission for app storage
                if(!checkPermission())
                {
                    requestPermission();
                }
                initializeDevice();
            }
        });
        initializationThread.start();
    }

    // For create Menu-Options
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    // For created Menu-Options and select items based on click events
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.configureSettings).setEnabled(true);
        menu.findItem(R.id.autosaveSettings).setEnabled(true);
        return true;
    }
	
    // On press back button send toast message for closing the App.
    // Based on user confirmation App will be closed.
    @Override
    public void onBackPressed() {
        if (pressedTime + 2000 > System.currentTimeMillis()) {
            if (device != null) {
                device.dispose();
                device = null;
            }
            if (terminate) {
                NBDevices.terminate();
            }
            int pid = android.os.Process.myPid();
            android.os.Process.killProcess(pid);
            //finishAffinity();
            //finish();
        } else {
            Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT).show();
        }
        pressedTime = System.currentTimeMillis();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.configureSettings:
                isConfigureSettings(item);
                break;
            case R.id.autosaveSettings:
                isConfigureSettings(item);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        if (device != null) {
            device.dispose();
            device = null;
        }
        if (terminate) {
            NBDevices.terminate();
        }
        super.onDestroy();
    }
	
    private int makePin(int chipNumber, int gpioNumber) {
        return (0x01000000 | (chipNumber<<16) | gpioNumber);
    }

    //Check storage permission for device calibration data(65210-S).
    private boolean checkPermission() {
        /*API-Level-30-Start*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        /*API-Level-30-End*/
        else
        {
            int readPermission = ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.READ_EXTERNAL_STORAGE);
            int writePermission = ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return readPermission == PackageManager.PERMISSION_GRANTED && writePermission == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        /*API-Level-30-Start*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", new Object[]{getApplicationContext().getPackageName()})));
                startActivityForResult(intent, PERMISSION_CALLBACK_CODE);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, PERMISSION_CALLBACK_CODE);
            }
        }
        /*API-Level-30-End*/
        else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, READ_WRITE_PERMISSION_REQUEST_CODE);
        }
    }

    /*API-Level-30-Start*/
    //Handling permission callback for Android 11 or above version
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_CALLBACK_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Permission allowed.", Toast.LENGTH_SHORT).show();
                    isPermissionGranted = true;
                } else {
                    Toast.makeText(this, "Allow permission for storage access!", Toast.LENGTH_SHORT).show();
                    isPermissionGranted = false;
                }
            }
        }
    }
    /*API-Level-30-End*/

    //Handling permission callback for below Android 11
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case READ_WRITE_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean READ_EXTERNAL_STORAGE = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean WRITE_EXTERNAL_STORAGE = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    if (READ_EXTERNAL_STORAGE && WRITE_EXTERNAL_STORAGE) {
                        Toast.makeText(this, "Permission allowed for storage access.", Toast.LENGTH_SHORT).show();
                        isPermissionGranted = true;
                    } else {
                        Toast.makeText(this, "Allow permission for storage access!", Toast.LENGTH_SHORT).show();
                        isPermissionGranted = false;
                    }
                }
                break;
        }
    }
    //
    // UI methods
    //

    //Allow settings configurations
    public boolean isConfigureSettings(MenuItem item)
    {
        item.setEnabled(true);
        if(isScanAndExtractInProgress)
        {
            Toast toast=Toast.makeText(getApplicationContext(),"Operation In-Progress",Toast.LENGTH_SHORT);
            toast.setMargin(50,50);
            toast.show();
            item.setEnabled(false);
            return true;
        }
        //Device is null or Session Not opened.
        if ((device == null) || (device != null && !device.isSessionOpen()))
        {
            Toast toast=Toast.makeText(getApplicationContext(),"Device is not ready",Toast.LENGTH_SHORT);
            toast.setMargin(50,50);
            toast.show();
            item.setEnabled(false);
            return true;
        }
        else
        {
            onConfigureMenuItem(item);
        }
        return true;
    }

    // Configure MenuItem feature
    void onConfigureMenuItem(MenuItem item) {
        final Context context = this;
        if (item.getItemId() == R.id.configureSettings) {
            LayoutInflater li = LayoutInflater.from(context);
            View promptsView = li.inflate(R.layout.menu_layout, null);
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    context);
            alertDialogBuilder.setView(promptsView);
            final CheckBox checkSpoof = (CheckBox) promptsView
                    .findViewById(R.id.spoofview);
            final EditText userInput = (EditText) promptsView
                    .findViewById(R.id.editTextDialogUserInput);


            userInput.setText(ANTISPOOF_THRESHOLD);
            userInput.setEnabled(false);
            //Retain Old-Value until New value set.
            if (isSpoofEnabled) {
                checkSpoof.setChecked(true);
                userInput.setEnabled(true);
            }
            validateCheckBox(checkSpoof, userInput);
            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    validateAndSetSpoof(userInput, checkSpoof);
                                }
                            })
                    .setNegativeButton("Reset",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    userInput.setEnabled(true);
                                    checkSpoof.setChecked(false);
                                    isSpoofEnabled = false;
                                    userInput.setText(DEFAULT_ANTISPOOF_THRESHOLD);
                                    ANTISPOOF_THRESHOLD = userInput.getText().toString();
                                    dialog.cancel();
                                }
                            });
            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        } else {
            LayoutInflater li = LayoutInflater.from(context);
            View promptsView = li.inflate(R.layout.auto_save_layout, null);
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    context);
            alertDialogBuilder.setView(promptsView);
            final CheckBox checkautosave = (CheckBox) promptsView
                    .findViewById(R.id.spoofview);
            final Spinner imageFormatSpinner = (Spinner) promptsView.findViewById(R.id.imageformatSpinner);
            final ArrayAdapter<CharSequence> imageFormatAdapter = ArrayAdapter.createFromResource(getApplicationContext(), R.array.image_format_list,
                    android.R.layout.simple_spinner_item);
            imageFormatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            imageFormatSpinner.setAdapter(imageFormatAdapter);
            imageFormatSpinner.setEnabled(false);
            imageFormatSpinner.setSelection(imageFormatSpinnerSelected);
            if (isAutoSaveEnabled) {
                checkautosave.setChecked(true);
                imageFormatSpinner.setEnabled(true);
            }
            checkautosave.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (checkautosave.isChecked()) {
                        imageFormatSpinner.setEnabled(true);
                    } else {
                        imageFormatSpinner.setEnabled(false);
                    }
                }
            });
            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    isAutoSaveEnabled = checkautosave.isChecked();
                                    imageFormatSpinnerSelected = (int) imageFormatSpinner.getSelectedItemId();
                                    CharSequence item = imageFormatAdapter.getItem(imageFormatSpinner.getSelectedItemPosition());
                                    imageFormatName = item.toString();
                                }
                            })
                    .setNegativeButton("Reset",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    isAutoSaveEnabled=false;
                                    imageFormatSpinnerSelected=0;
                                }
                            });
            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        }

    }

    void validateCheckBox(final CheckBox checkSpoof, final EditText userInput)
    {
        checkSpoof.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(checkSpoof.isChecked()) {
                    userInput.setEnabled(true);
                }
                else{
                    userInput.setEnabled(false);
                }
            }
        });
    }

    // Validate and Enable/Set spoof threshold
    void validateAndSetSpoof(EditText userInput, CheckBox checkSpoof){
        if(userInput.getText().toString().isEmpty()){
            userInput.setText(DEFAULT_ANTISPOOF_THRESHOLD);
        }
        ANTISPOOF_THRESHOLD = userInput.getText().toString();
        spoofThreshold = Integer.parseInt(ANTISPOOF_THRESHOLD);
        if(spoofThreshold <= MIN_ANTISPOOF_THRESHOLD)
        {
            spoofThreshold = MIN_ANTISPOOF_THRESHOLD;
            ANTISPOOF_THRESHOLD = Integer.toString(MIN_ANTISPOOF_THRESHOLD);
        }
        if(spoofThreshold >= MAX_ANTISPOOF_THRESHOLD)
        {
            spoofThreshold = MAX_ANTISPOOF_THRESHOLD;
            ANTISPOOF_THRESHOLD = Integer.toString(MAX_ANTISPOOF_THRESHOLD);
        }
        if(checkSpoof.isChecked()) {
            checkSpoof.setChecked(true);
            isSpoofEnabled = true;
            enableSpoof();
        }
        else{
            checkSpoof.setChecked(false);
            isSpoofEnabled = false;
            device.setParameter(CONFIGURE_ANTISPOOF, DISABLE_ANTISPOOF);
        }
    }

    void enableSpoof()
    {
        device.setParameter(CONFIGURE_ANTISPOOF, ENABLE_ANTISPOOF);
        device.setParameter(CONFIGURE_ANTISPOOF_THRESHOLD, spoofThreshold);
    }

    void onInitializationSuccess() {
        btnScanAndExtract.setEnabled(true);
    }

    void onScanExtractCompleted() {
        if (device == null)
        {
            //device is null
            showMessage("Device Unplugged or No Active Session.", true);
        }
        btnScanAndExtract.setEnabled(true);
        isScanAndExtractInProgress = false;
    }

    void showResult(byte[] image, String text) {
        if (image != null) {
            imageView.setImageBitmap(convertToBitmap(scanFormatInfo, image));
        }
        else {
            imageView.setImageResource(R.drawable.app_icon);
        }
        Log.i("MainActivity", text);
        textResults.setText(text);
    }

    //
    // Non-UI methods
    //
    private void initializeDevice() {
        try {
            if(deviceInit()) {
                showMessage("Device initialized");
            }
            else{
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onInitializationSuccess();
                }
            });
        } catch (NextBiometricsException ex) {
            showMessage("ERROR: NEXT Biometrics SDK error: " + ex.toString(), true);
            ex.printStackTrace();
        } catch (Throwable ex) {
            showMessage("ERROR: " + ex.toString(), true);
            ex.printStackTrace();
        }
    }

    private boolean isAllowOneTimeBG(NBDevice dev)
    {
        NBDeviceType devType = dev.getType();
        //For the below device types alone one-time BG capture supported. NBEnhance supported modules.
        if( devType == NBDeviceType.NB2020U  ||
            devType == NBDeviceType.NB2023U  ||
            devType == NBDeviceType.NB2033U  ||
            devType == NBDeviceType.NB65200U ||
            devType == NBDeviceType.NB65210S
          )
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    private boolean deviceInit() {
        try {
            NBDevices.initialize(getApplicationContext());
            terminate = true;
            // wait for callback here
            // or for the sake of simplicity, sleep
            showMessage("Waiting for a USB device");
            for (int i = 0; i < 50; i++) {
                Thread.sleep(500);
                if(NBDevices.getDevices().length != 0)
                    break;
            }
            NBDevice[] devices = NBDevices.getDevices();
            if (devices.length == 0) {
                Log.i("MainActivity", "No USB device connected");
                showMessage("No USB device found, trying an SPI device");
                try {
                    isSpi = false;
                    device = NBDevice.connectToSpi(DEFAULT_SPI_NAME, DEFAULT_SYSFS_PATH, DEFAULT_SPICLK, DEFAULT_AWAKE_PIN_NUMBER, DEFAULT_RESET_PIN_NUMBER, DEFAULT_CHIP_SELECT_PIN_NUMBER, FLAGS);
                    isSpi = true;
                } catch (Exception e) {
                    // showMessage("Problem when opening SPI device: " + e.getMessage());
                }
                if (device == null) {
                    Log.i("MainActivity", "No SPI devices connected");
                    showMessage("No device connected", true);
                    return false;
                }
                if((devices.length == 0) && !isSpi){
                    showMessage("No device connected.", true);
                    NBDevices.terminate();
                    device.dispose();
                    device = null;
                    return false;
                }
            } else {
                device = devices[0];
                isSpi = false;
            }

            openSession();
            // If the device requires external calibration data (NB-65210-S), load or create them
            if (device != null && device.getCapabilities().requiresExternalCalibrationData) {
                solveCalibrationData(device);
            }

            NBDeviceScanFormatInfo[] scanFormats = device.getSupportedScanFormats();
            if (scanFormats.length == 0)
                throw new Exception("No supported formats found!");
            scanFormatInfo = scanFormats[0];

            if ((device != null) && isAllowOneTimeBG(device))
            {
                device.setParameter(NBDevice.NB_DEVICE_PARAMETER_SUBTRACT_BACKGROUND, ENABLE_BACKGROUND_SUBTRACTION);
                bgscanResult = device.scanBGImage(scanFormatInfo);
            }
            return true;
        }
        catch (NextBiometricsException ex) {
            showMessage("ERROR: NEXT Biometrics SDK error: " + ex.toString(), true);
            ex.printStackTrace();
            return false;
        } catch (Throwable ex) {
            showMessage("ERROR: " + ex.toString(), true);
            ex.printStackTrace();
            return false;
        }

    }
    private void openSession(){
        if (device != null && !device.isSessionOpen()) {
            byte[] cakId = "DefaultCAKKey1\0".getBytes();
            byte[] cak = {
                    (byte) 0x05, (byte) 0x4B, (byte) 0x38, (byte) 0x3A, (byte) 0xCF, (byte) 0x5B, (byte) 0xB8, (byte) 0x01, (byte) 0xDC, (byte) 0xBB, (byte) 0x85, (byte) 0xB4, (byte) 0x47, (byte) 0xFF, (byte) 0xF0, (byte) 0x79,
                    (byte) 0x77, (byte) 0x90, (byte) 0x90, (byte) 0x81, (byte) 0x51, (byte) 0x42, (byte) 0xC1, (byte) 0xBF, (byte) 0xF6, (byte) 0xD1, (byte) 0x66, (byte) 0x65, (byte) 0x0A, (byte) 0x66, (byte) 0x34, (byte) 0x11
            };
            byte[] cdkId = "Application Lock\0".getBytes();
            byte[] cdk = {
                    (byte) 0x6B, (byte) 0xC5, (byte) 0x51, (byte) 0xD1, (byte) 0x12, (byte) 0xF7, (byte) 0xE3, (byte) 0x42, (byte) 0xBD, (byte) 0xDC, (byte) 0xFB, (byte) 0x5D, (byte) 0x79, (byte) 0x4E, (byte) 0x5A, (byte) 0xD6,
                    (byte) 0x54, (byte) 0xD1, (byte) 0xC9, (byte) 0x90, (byte) 0x28, (byte) 0x05, (byte) 0xCF, (byte) 0x5E, (byte) 0x4C, (byte) 0x83, (byte) 0x63, (byte) 0xFB, (byte) 0xC2, (byte) 0x3C, (byte) 0xF6, (byte) 0xAB
            };
            byte[] defaultAuthKey1Id = "AUTH1\0".getBytes();
            byte[] defaultAuthKey1 = {
                    (byte) 0xDA, (byte) 0x2E, (byte) 0x35, (byte) 0xB6, (byte) 0xCB, (byte) 0x96, (byte) 0x2B, (byte) 0x5F, (byte) 0x9F, (byte) 0x34, (byte) 0x1F, (byte) 0xD1, (byte) 0x47, (byte) 0x41, (byte) 0xA0, (byte) 0x4D,
                    (byte) 0xA4, (byte) 0x09, (byte) 0xCE, (byte) 0xE8, (byte) 0x35, (byte) 0x48, (byte) 0x3C, (byte) 0x60, (byte) 0xFB, (byte) 0x13, (byte) 0x91, (byte) 0xE0, (byte) 0x9E, (byte) 0x95, (byte) 0xB2, (byte) 0x7F
            };
            NBDeviceSecurityModel security = NBDeviceSecurityModel.get(device.getCapabilities().securityModel);
            if (security == NBDeviceSecurityModel.Model65200CakOnly) {
                device.openSession(cakId, cak);
            } else if (security == NBDeviceSecurityModel.Model65200CakCdk) {
                try {
                    device.openSession(cdkId, cdk);
                    device.SetBlobParameter(NBDevice.BLOB_PARAMETER_SET_CDK, null);
                    device.closeSession();
                } catch (RuntimeException ex) {
                }
                device.openSession(cakId, cak);
                device.SetBlobParameter(NBDevice.BLOB_PARAMETER_SET_CDK, cdk);
                device.closeSession();
                device.openSession(cdkId, cdk);
            } else if (security == NBDeviceSecurityModel.Model65100) {
                device.openSession(defaultAuthKey1Id, defaultAuthKey1);
            }
        }
    }

    private void scanAndExtract() {
        //Hide Menu before scan and extract starts
        isScanAndExtractInProgress = true;

        //Check storage permission before scan and extract starts
        while(!checkPermission()) {
            requestPermission();
        }

        NBBiometricsContext context = null;
        try {
            if ((device == null) || (device != null && !device.isSessionOpen())) {
                //During Reset/unplug-device Session Closed. Need to re-Initilize Device
                if(deviceInit())
                    showMessage("Device initialized");

            }
            if (device != null) {
                // If the device requires external calibration data (NB-65210-S), load or create them
                if (device.getCapabilities().requiresExternalCalibrationData) {
                    solveCalibrationData(device);
                }
                context = new NBBiometricsContext(device);
                NBBiometricsExtractResult extractResult = null;

                showMessage("");
                showMessage("Extracting fingerprint template, please put your finger on sensor!");
                previewListener.reset();
                timeStart = timeStop = 0;
                quality = 0;
                success = false;
                try {
                    timeStart = currentTimeMillis();
                    //Hide Menu before scan and extract starts.
                    isScanAndExtractInProgress = true;
                    if(isSpoofEnabled) enableSpoof();
                    //Enable Image preview for FAP20
                    //device.setParameter(410,1);
                    extractResult = context.extract(NBBiometricsTemplateType.ISO, NBBiometricsFingerPosition.UNKNOWN, scanFormatInfo, previewListener);
                    timeStop = currentTimeMillis();
                }
                catch (Exception ex) {
                    if (!isSpi) throw ex;

                    // Workaround for a specific customer device problem: If the SPI is idle for certain time, it is put to sleep in a way which breaks the communication
                    // The workaround is to reopen the SPI connection, which resets the communication
                    if (isSpi && (ex.getMessage().equalsIgnoreCase(retrySPICause))) {
                        //Retry Max Times for SPI
                        context.dispose();
                        context = null;
                        device.dispose();
                        device = null;
                        device = NBDevice.connectToSpi(DEFAULT_SPI_NAME, DEFAULT_SYSFS_PATH, DEFAULT_SPICLK, DEFAULT_AWAKE_PIN_NUMBER, DEFAULT_RESET_PIN_NUMBER, DEFAULT_CHIP_SELECT_PIN_NUMBER, FLAGS);
                        // If the device requires external calibration data (NB-65210-S), load or create them
                        if (device != null && device.getCapabilities().requiresExternalCalibrationData) {
                            solveCalibrationData(device);
                        }

                        NBDeviceScanFormatInfo[] scanFormats = device.getSupportedScanFormats();
                        if (scanFormats.length == 0)
                            throw new Exception("No supported formats found!");
                        scanFormatInfo = scanFormats[0];
                        // And retry the extract operation
                        context = new NBBiometricsContext(device);
                        timeStart = currentTimeMillis();
                        if(isSpoofEnabled) enableSpoof();
                        //Enable Image preview for FAP20
                        //device.setParameter(410,1);
                        extractResult = context.extract(NBBiometricsTemplateType.ISO, NBBiometricsFingerPosition.UNKNOWN, scanFormatInfo, previewListener);
                        timeStop = currentTimeMillis();
                    }
                    else
                    {
                        //If block handled for IO Command failed exception for SPI.
                        throw ex;
                    }
                }
                if (extractResult.getStatus() != NBBiometricsStatus.OK)
                {
                    throw new Exception("Extraction failed, reason: " + extractResult.getStatus());
                }
                //Antispoof check
                int tmpSpoofThreshold = Integer.parseInt(ANTISPOOF_THRESHOLD);
                if(isSpoofEnabled && isValidSpoofScore && previewListener.getSpoofScore() <= tmpSpoofThreshold)
                {
                    throw new Exception("Extraction failed, reason: " + spoofCause);
                }
                showMessage("Extracted successfully!");
                NBBiometricsTemplate template = extractResult.getTemplate();
                quality = template.getQuality();
                showResultOnUiThread(previewListener.getLastImage(),
                        String.format("Last scan = %d msec, Image process = %d msec, Extract = %d msec, Total time = %d msec\nTemplate quality = %d, Last finger detect score = %d",
                                previewListener.getTimeScanEnd() - previewListener.getTimeScanStart(), previewListener.getTimeOK() - previewListener.getTimeScanEnd(), timeStop - previewListener.getTimeOK(), timeStop - previewListener.getTimeScanStart(), quality, previewListener.getFdetScore()));
                if(isAutoSaveEnabled){
                    saveImageApi(imageFormatName,"Extraction_Template");
                }
                // Verification
                //
                showMessage("");
                showMessage("Verifying fingerprint, please put your finger on sensor!");
                previewListener.reset();
                context.dispose();
                context = null;
                context = new NBBiometricsContext(device);
                timeStart = currentTimeMillis();
                //Enable Image preview for FAP20
                //device.setParameter(410,1);
                NBBiometricsVerifyResult verifyResult = context.verify(NBBiometricsTemplateType.ISO, NBBiometricsFingerPosition.UNKNOWN, scanFormatInfo, previewListener, template, NBBiometricsSecurityLevel.NORMAL);
                timeStop = currentTimeMillis();
                if (verifyResult.getStatus() != NBBiometricsStatus.OK)
                {
                    throw new Exception("Not verified, reason: " + verifyResult.getStatus());
                }
                if(isSpoofEnabled && isValidSpoofScore && previewListener.getSpoofScore() <= tmpSpoofThreshold)
                {
                    throw new Exception("Not verified, reason: " + spoofCause);
                }
                showMessage("Verified successfully!");
                showResultOnUiThread(previewListener.getLastImage(),
                        String.format("Last scan = %d msec, Image process = %d msec, Extract+Verify = %d msec, Total time = %d msec\nMatch score = %d, Last finger detect score = %d",
                                previewListener.getTimeScanEnd() - previewListener.getTimeScanStart(), previewListener.getTimeOK() - previewListener.getTimeScanEnd(), timeStop - previewListener.getTimeOK(), timeStop - previewListener.getTimeScanStart(), verifyResult.getScore(), previewListener.getFdetScore()));
                if(isAutoSaveEnabled){
                    saveImageApi(imageFormatName,"Verification_Template");
                }
                // Identification
                //
                showMessage("");
                List<AbstractMap.SimpleEntry<Object, NBBiometricsTemplate>> templates = new LinkedList<>();
                templates.add(new AbstractMap.SimpleEntry<Object, NBBiometricsTemplate>("TEST", template));
                // add more templates

                showMessage("Identifying fingerprint, please put your finger on sensor!");
                previewListener.reset();
                context.dispose();
                context = null;
                context = new NBBiometricsContext(device);
                timeStart = currentTimeMillis();
                //Enable Image preview for FAP20
                //device.setParameter(410,1);
                NBBiometricsIdentifyResult identifyResult = context.identify(NBBiometricsTemplateType.ISO, NBBiometricsFingerPosition.UNKNOWN, scanFormatInfo, previewListener, templates.iterator(), NBBiometricsSecurityLevel.NORMAL);
                timeStop = currentTimeMillis();
                if (identifyResult.getStatus() != NBBiometricsStatus.OK)
                {
                    throw new Exception("Not identified, reason: " + identifyResult.getStatus());
                }
                if(isSpoofEnabled && isValidSpoofScore && previewListener.getSpoofScore() <= tmpSpoofThreshold)
                {
                    throw new Exception("Not identified, reason: " + spoofCause);
                }
                showMessage("Identified successfully with fingerprint: " + identifyResult.getTemplateId());
                showResultOnUiThread(previewListener.getLastImage(),
                        String.format("Last scan = %d msec, Image process = %d msec, Extract+Identify = %d msec, Total time = %d msec\nMatch score = %d, Last finger detect score = %d",
                                previewListener.getTimeScanEnd() - previewListener.getTimeScanStart(), previewListener.getTimeOK() - previewListener.getTimeScanEnd(), timeStop - previewListener.getTimeOK(), timeStop - previewListener.getTimeScanStart(), verifyResult.getScore(), previewListener.getFdetScore()));
                if(isAutoSaveEnabled){
                    saveImageApi(imageFormatName,"Identification_Template");
                }
                // Save template
                byte[] binaryTemplate = context.saveTemplate(template);
                showMessage(String.format("Extracted template length: %d bytes", binaryTemplate.length));
                String base64Template = Base64.encodeToString(binaryTemplate, 0);
                showMessage("Extracted template: " + base64Template);

                // Store template to file
                String dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/NBCapturedImages/";
                String filePath = dirPath + createFileName()+"-ISO-Template.bin";
                File files = new File(dirPath);
                files.mkdirs();
                showMessage("Saving ISO template to " + filePath);
                FileOutputStream fos = new FileOutputStream(filePath);
                fos.write(binaryTemplate);
                fos.close();
                success = true;
            }
        } catch(NextBiometricsException ex){
            showMessage("ERROR: NEXT Biometrics SDK error: " + ex.toString(), true);
            ex.printStackTrace();
        } catch(Throwable ex){
            showMessage("ERROR: " + ex.getMessage(), true);
            ex.printStackTrace();
        }
        if (context != null) {
            context.dispose();
            context = null;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onScanExtractCompleted();
            }
        });
    }

    private void solveCalibrationData(NBDevice device) throws Exception {
        final String paths = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/NBData/" + device.getSerialNumber() + "_calblob.bin";
        new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/NBData/").mkdirs();
        File file = new File(paths);
        if(!file.exists()) {
            // Ask the user whether he wants to calibrate the device
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            synchronized(MainActivity.this) {
                                dialogResult = (which == DialogInterface.BUTTON_POSITIVE);
                                MainActivity.this.notifyAll();
                            }
                        }
                    };
                    new AlertDialog.Builder(MainActivity.this).setMessage(
                            "This device is not calibrated yet. Do you want to calibrate it?\r\n\r\n" +
                                    "If yes, at first perfectly clean the sensor, and only then select the YES button."
                    ).setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();
                }
            });

            synchronized (MainActivity.this) {
                MainActivity.this.wait();
                if (!dialogResult) throw new Exception("The device is not calibrated");
            }
            showMessage("Creating calibration data: " + paths);
            showMessage("This operation may take several minutes.");
            try {
                byte[] data = device.GenerateCalibrationData();
                FileOutputStream fos = new FileOutputStream(paths);
                fos.write(data);
                fos.close();
                showMessage("Calibration data created");
            }
            catch(final Exception e) {
                showMessage(e.getMessage(), true);
            }
        }
        if(file.exists()) {
            int size = (int) file.length();
            byte[] bytes = new byte[size];
            try {
                BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
                buf.read(bytes, 0, bytes.length);
                buf.close();
            }
            catch (IOException ex) {}
            device.SetBlobParameter(NBDevice.BLOB_PARAMETER_CALIBRATION_DATA, bytes);
        }
        else {
            throw new Exception("Missing compensation data - " + paths);
        }
    }

    private static byte[] readFile(String filePath) throws IOException {
        File file = new File(filePath);
        byte []buffer = new byte[(int) file.length()];
        InputStream ios = null;
        try {
            ios = new FileInputStream(file);
            if ( ios.read(buffer) == -1 ) {
                throw new IOException("EOF reached while trying to read the whole file");
            }
        } finally {
            if (ios != null)
                ios.close();
        }

        return buffer;
    }

    private byte[] readFileResource(String resourcePath) throws IOException {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is == null)
            throw new IOException("cannot find resource: "+resourcePath);
        return getBytesFromInputStream(is);
    }

    private static byte[] getBytesFromInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int len; (len = is.read(buffer)) != -1;) {
            os.write(buffer, 0, len);
        }
        os.flush();
        return os.toByteArray();
    }

    private static Bitmap convertToBitmap(NBDeviceScanFormatInfo formatInfo, byte[] image){
        IntBuffer buf = IntBuffer.allocate(image.length);
        for (byte pixel : image) {
            int grey = pixel & 0x0ff;
            buf.put(Color.argb(255, grey, grey, grey));
        }
        bufferBytes=image;
        templateBmp=Bitmap.createBitmap(buf.array(), formatInfo.getWidth(), formatInfo.getHeight(), Bitmap.Config.ARGB_8888);
        return templateBmp;
    }

    private void showResultOnUiThread(final byte[] image, final String text) {
        this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                showResult(image, text);
            }
        });
    }

    private void showMessage(final String message){
        showMessage(message, false);
    }

    private void showMessage(final String message, final boolean isErrorMessage){
        this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (isErrorMessage) {
                    Log.e("MainActivity", message);
                }
                else {
                    Log.i("MainActivity", message);
                }
                LinearLayout messagesHolder = (LinearLayout) findViewById(R.id.messagesHolder);
                ScrollView scrollView = (ScrollView) findViewById(R.id.scrollView1);
                TextView singleMessage = new TextView(getApplicationContext());
                if(isErrorMessage) singleMessage.setTextColor(getResources().getColor(R.color.error_message_color));
                singleMessage.append(message);
                messagesHolder.addView(singleMessage);
                MainActivity.this.lastMessage = singleMessage;
            }
        });

        // Scroll to the end. This must be done with a delay, after the last message is drawn
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ScrollView scrollView = (ScrollView) findViewById(R.id.scrollView1);
                        scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        }, 100);
    }

    private void updateMessage(final String message) {
        this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Log.i("MainActivity", String.format("%d: %s", currentTimeMillis(), message));
                if (MainActivity.this.lastMessage != null) {
                    MainActivity.this.lastMessage.setText(message);
                }
            }
        });
    }

    private void clearMessages() {
        this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Log.i("MainActivity", "-------------------------------------------");
                LinearLayout messagesHolder = (LinearLayout) findViewById(R.id.messagesHolder);
                messagesHolder.removeAllViewsInLayout();
                MainActivity.this.lastMessage = null;
            }
        });
    }

    public static String createFileName() {
        final String DEFAULT_FILE_PATTERN = "yyyy-MM-dd-HH-mm-ss";
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat(DEFAULT_FILE_PATTERN);
        return format.format(date);
    }

    private void writeImage(String imageFormatName, String filePath) {
        FileOutputStream out = null;
        try {

            switch (imageFormatName) {
                case "JPG":
                    out = new FileOutputStream(filePath);
                    templateBmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    out.flush();
                    out.close();
                    break;
                case "PNG":
                    out = new FileOutputStream(filePath);
                    templateBmp.compress(Bitmap.CompressFormat.PNG, 90, out);
                    out.flush();
                    out.close();
                    break;
                case "RAW":
                    out = new FileOutputStream(filePath);
                    out.write(bufferBytes);
                    out.close();
                    break;
                default:
                    AndroidBmpUtil ScannedBMP = new AndroidBmpUtil();
                    try {
                        ScannedBMP.save(templateBmp, filePath);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
            }

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private void saveImageApi(String imageFormatName, String featureProcess) {
        String dirPath = null;
        String saveName = null;
        String filePath;

        if (imageFormatName == null)
            return;

        switch (imageFormatName) {
            case "JPG":
                saveName = createFileName() + "_" + featureProcess + ".jpg";
                break;
            case "PNG":
                saveName = createFileName() + "_" + featureProcess + ".png";
                break;
            case "RAW":
                saveName = createFileName() + "_" + featureProcess + ".raw";
                break;
            case "BMP":
                saveName = createFileName() + "_" + featureProcess + ".bmp";
                break;
        }

        try {
            dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/NBCapturedImages/";
            filePath = dirPath + saveName;
            File files = new File(dirPath);
            files.mkdirs();
            writeImage(imageFormatName, filePath);
            showMessage("Image save in : " + filePath);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    public class AndroidBmpUtil {

        private static final int BMP_WIDTH_OF_TIMES = 4;
        private static final int BYTE_PER_PIXEL = 3;

        public boolean save(Bitmap orgBitmap, String filePath) throws IOException {
            if(orgBitmap == null){
                return false;
            }

            if(filePath == null){
                return false;
            }

            int width = orgBitmap.getWidth();
            int height = orgBitmap.getHeight();

            //image empty data size
            //reason : the amount of bytes per image row must be a multiple of 4 (requirements of bmp format)
            byte[] emptyBytesPerRow = null;
            boolean hasEmpty = false;
            int rowWidthInBytes = BYTE_PER_PIXEL * width; //source image width * number of bytes to encode one pixel.
            if(rowWidthInBytes % BMP_WIDTH_OF_TIMES > 0){
                hasEmpty=true;
                //the number of empty bytes we need to add on each row
                emptyBytesPerRow = new byte[(BMP_WIDTH_OF_TIMES-(rowWidthInBytes % BMP_WIDTH_OF_TIMES))];
                //just fill an array with the empty bytes we need to append at the end of each row
                for(int emptyBytesPerRowIndex = 0; emptyBytesPerRowIndex < emptyBytesPerRow.length; emptyBytesPerRowIndex++){
                    emptyBytesPerRow[emptyBytesPerRowIndex] = (byte)0xFF;
                }
            }

            //an array to receive the pixels from the source image
            int[] pixels = new int[width * height];

            //the number of bytes used in the file to store raw image data (excluding file headers)
            int imageSize = (rowWidthInBytes + (hasEmpty ? emptyBytesPerRow.length : 0)) * height;
            //file headers size
            int imageDataOffset = 0x36;

            //final size of the file
            int fileSize = imageSize + imageDataOffset;

            //Android Bitmap Image Data
            orgBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            //ByteArrayOutputStream baos = new ByteArrayOutputStream(fileSize);
            ByteBuffer buffer = ByteBuffer.allocate(fileSize);

            /**
             * BITMAP FILE HEADER Write Start
             **/
            buffer.put((byte)0x42);
            buffer.put((byte)0x4D);

            //size
            buffer.put(writeInt(fileSize));

            //reserved
            buffer.put(writeShort((short)0));
            buffer.put(writeShort((short)0));

            //image data start offset
            buffer.put(writeInt(imageDataOffset));

            /** BITMAP FILE HEADER Write End */

            //*******************************************

            /** BITMAP INFO HEADER Write Start */
            //size
            buffer.put(writeInt(0x28));

            //width, height
            //if we add 3 empty bytes per row : it means we add a pixel (and the image width is modified.
            buffer.put(writeInt(width + (hasEmpty ? (emptyBytesPerRow.length == 3 ? 1 : 0) : 0)));
            buffer.put(writeInt(height));

            //planes
            buffer.put(writeShort((short)1));

            //bit count
            buffer.put(writeShort((short)24));

            //bit compression
            buffer.put(writeInt(0));

            //image data size
            buffer.put(writeInt(imageSize));

            //horizontal resolution in pixels per meter
            buffer.put(writeInt(0));

            //vertical resolution in pixels per meter (unreliable)
            buffer.put(writeInt(0));

            buffer.put(writeInt(0));

            buffer.put(writeInt(0));

            /** BITMAP INFO HEADER Write End */

            int row = height;
            int col = width;
            int startPosition = (row - 1) * col;
            int endPosition = row * col;
            while( row > 0 ){
                for(int pixelsIndex = startPosition; pixelsIndex < endPosition; pixelsIndex++ ){
                    buffer.put((byte)(pixels[pixelsIndex] & 0x000000FF));
                    buffer.put((byte)((pixels[pixelsIndex] & 0x0000FF00) >> 8));
                    buffer.put((byte)((pixels[pixelsIndex] & 0x00FF0000) >> 16));
                }
                if(hasEmpty){
                    buffer.put(emptyBytesPerRow);
                }
                row--;
                endPosition = startPosition;
                startPosition = startPosition - col;
            }

            try {
                FileOutputStream fos = new FileOutputStream(filePath, false);
                fos.write(buffer.array());
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return true;
        }

        //Write integer to little-endian
        private byte[] writeInt(int value) throws IOException {
            byte[] byteArray = new byte[4];

            byteArray[0] = (byte)(value & 0x000000FF);
            byteArray[1] = (byte)((value & 0x0000FF00) >> 8);
            byteArray[2] = (byte)((value & 0x00FF0000) >> 16);
            byteArray[3] = (byte)((value & 0xFF000000) >> 24);

            return byteArray;
        }

        //Write short to little-endian byte array
        private byte[] writeShort(short value) throws IOException {
            byte[] byteArray = new byte[2];

            byteArray[0] = (byte)(value & 0x00FF);
            byteArray[1] = (byte)((value & 0xFF00) >> 8);

            return byteArray;
        }
    }
    }
