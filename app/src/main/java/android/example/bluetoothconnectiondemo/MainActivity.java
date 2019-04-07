package android.example.bluetoothconnectiondemo;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    // Debug tag
    private static final String TAG = "MainActivity";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Layout Views
    private TextView mStatusText;
    private TextView mErrorCodeText;
    private Button mSendButton;
    private EditText mIntegrationTime;

    // Local Bluetooth Adapter
    private BluetoothAdapter mBluetoothAdapter = null;

    // Name of connected device
    private String mConnectedDeviceName = "MACH-WX9";

    // Address of the connected device
    private String mConnectedDeviceAddress = "B4:86:55:F5:D6:AB";

    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;

    // Member object for the chat services
    private BluetoothService mBluetoothService = null;




    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:

                    return true;
                case R.id.navigation_dashboard:

                    return true;
                case R.id.navigation_notifications:

                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStatusText = findViewById(R.id.status_text);
        mErrorCodeText = findViewById(R.id.error_code_text);
        mSendButton = findViewById(R.id.send_information_button);
        mIntegrationTime = findViewById(R.id.integration_time_editText);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        // Get the local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            MainActivity activity = this;
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }

        // Ask user to let the app access coarse location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled
        // setupCommunication() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else if (mBluetoothService == null) {
            setupCommunication();
        }
    }

    @Override
    protected  void onDestroy() {
        super.onDestroy();
        mBluetoothAdapter.cancelDiscovery();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mBluetoothService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mBluetoothService.getState() == BluetoothService.STATE_NONE) {
                // Start the Bluetooth chat services
                mBluetoothService.start();
            }
        }
    }

    // Set up the UI and background operations for communication
    private void setupCommunication() {
        Log.d(TAG, "setupCommunication()");

        // Initialize the send button with a listener for click events
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send information using content of the app.
                String integrationTime = mIntegrationTime.getText().toString();
                sendInformation(integrationTime);

            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mBluetoothService = new BluetoothService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    // Makes this device discoverable for 300 seconds (5 minutes).
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    // sends information
    // @param information A string of text to send.
    private void sendInformation(String information) {
        // Check that we're actually connected before trying anything
        if (mBluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, "Error, No bluetooth device connected.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (information.length() > 0) {
            // Get the message bytes and tell the BluetoothService to write
            byte[] send = information.getBytes();
            mBluetoothService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            // ??? I don't know why we have to do this
            mOutStringBuffer.setLength(0);
            mIntegrationTime.setText(mOutStringBuffer);
        }
    }

    // The Handler that gets information back from the BluetoothService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            mStatusText.setText("Now connected to: " + mConnectedDeviceName);
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            mStatusText.setText("Connecting...");
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            mStatusText.setText("Not Connected");
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mErrorCodeText.setText(writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mErrorCodeText.setText(readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    mStatusText.setText("Connected to: " + mConnectedDeviceName);
                    break;
                case Constants.MESSAGE_ERROR:
            }
        }
    };

    // Establish connection with other device
    // @param secure Socket Security type - Secure (true) , Insecure (false)
    private void connectDevice(boolean secure) {
        //Get the device MAC address
        // This is hardcoded in to this example
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mConnectedDeviceAddress);
        // Attempt to connect to the device
        mBluetoothService.connect(device, secure);
    }

    public void connectToLaptop(View view) {
        connectDevice(true);
    }

    public void enableBT(View view) {
        setupCommunication();
    }
}


