package com.aaas.cpmcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.aaas.cpmcontroller.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG: String = MainActivity::class.simpleName.toString()

    private lateinit var binding: ActivityMainBinding

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var hc05BluetoothDevice: BluetoothDevice
    private lateinit var bluetoothSocket: BluetoothSocket

    private var isHC05Connected = false
    private var isHC05Paired = false
    private var isBluetoothEnabled = false

    private lateinit var resettingAlertDialog: AlertDialog
    private lateinit var closingAlertDialog: AlertDialog

    private lateinit var exitAlertDialog: AlertDialog
    private lateinit var connectionStatusAlertDialog: AlertDialog

    val HC05_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")


    /**
     * Bluetooth related things
     * and Broadcast Receivers
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission", "UseCompatLoadingForDrawables")
    private var bluetoothEnableResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                Log.d(TAG, "Bluetooth Enabled")
                isBluetoothEnabled = true
                changeBluetoothStatusTextView(true)
            } else {
                showExitAlertDialog()
            }
        }

    @SuppressLint("NewApi", "MissingPermission")
    private fun connectBluetooth() {
        try {
            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            bluetoothAdapter = bluetoothManager.adapter
            Log.d(TAG, "Enabling bluetooth")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableResultLauncher.launch(enableBtIntent)

            val bluetoothActionStateIntentFilter =
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(
                bluetoothStateChangeBroadcastReceiver,
                bluetoothActionStateIntentFilter
            )
        } catch (e: Exception) {
            Snackbar.make(
                binding.cardHome,
                "This device won't support Bluetooth",
                Snackbar.LENGTH_INDEFINITE
            ).show()
        }
    }

    private val bluetoothStateChangeBroadcastReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            @RequiresApi(Build.VERSION_CODES.M)
            override fun onReceive(context: Context?, intent: Intent) {
                val action = intent.action
                // When discovery finds a device
                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            isBluetoothEnabled = false
                            Log.d(TAG, "onReceive: STATE OFF")
                        }
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            isBluetoothEnabled = false
                            Log.d(TAG, "bluetoothStateChangeBroadcastReceiver: STATE TURNING OFF")
                            changeBluetoothStatusTextView(false)
                            showExitAlertDialog()
                        }
                        BluetoothAdapter.STATE_ON -> {
                            isBluetoothEnabled = true
                            Log.d(
                                TAG, "bluetoothStateChangeBroadcastReceiver: STATE ON"
                            )
                        }
                        BluetoothAdapter.STATE_TURNING_ON -> Log.d(
                            TAG, "bluetoothStateChangeBroadcastReceiver: STATE TURNING ON"
                        )
                    }
                }
            }
        }

//    @SuppressLint("MissingPermission")
//    private fun checkHC05PairStatus() {
//        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
//        pairedDevices?.forEach { device ->
//            val deviceName = device.name
//            val deviceHardwareAddress = device.address // MAC address
//
//            if (deviceName.equals("HC-05")) {
//                isHC05Paired = true
//                hc05BluetoothDevice = device
//                hc05BluetoothDevice.connectGatt(this@MainActivity, true);
//                Log.d(TAG, "HC-05 Paired")
//            }
//        }
//    }

    private val discoverDevicesBroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission", "NewApi")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action.toString()) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = device?.name
                    val deviceHardwareAddress = device?.address // MAC address

                    Log.d(TAG, "Device $deviceName : $deviceHardwareAddress")

                    if (deviceName.equals("HC-05")) {
                        hc05BluetoothDevice = device!!
                        ConnectThread().start()
                    }
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(TAG, "ACTION_ACL_CONNECTED")
                    isHC05Connected = true
                }

                BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED -> {
                    Log.d(TAG, "ACTION_ACL_DISCONNECT_REQUESTED")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d(TAG, "ACTION_ACL_DISCONNECTED")
                    changeCPMDeviceStatusTextView(false)
                    isHC05Connected = false
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoverDevices() {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        showConnectionStatusDialog()

        bluetoothAdapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread : Thread() {

        @RequiresApi(Build.VERSION_CODES.M)
        override fun run() {
            bluetoothAdapter.cancelDiscovery()
            bluetoothSocket = hc05BluetoothDevice.createRfcommSocketToServiceRecord(HC05_UUID)

            if (!bluetoothSocket.isConnected) {
                try {
                    bluetoothSocket.connect()
                    connectionStatusAlertDialog?.dismiss()
                    changeCPMDeviceStatusTextView(true)
                } catch (e: java.lang.Exception) {
                    Log.d(TAG, "Cannot connect to HC-05")
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.M)
        fun close() {
            try {
                Log.d(TAG, "Connect thread close called")
                changeCPMDeviceStatusTextView(false)
                connectionStatusAlertDialog?.dismiss()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    /**
     * Permissions related things
     */
    private fun hasFineLocationPermission() =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasCoarseLocationPermission() =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasBluetoothPermission() =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                100
            )
        } else {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                100
            )
        }
    }

    /**
     * Lifecycle related
     */
    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bluetoothStateChangeFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateChangeBroadcastReceiver, bluetoothStateChangeFilter)

        val discoverDevicesFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        discoverDevicesFilter.addAction(BluetoothDevice.ACTION_FOUND)
        discoverDevicesFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        discoverDevicesFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        discoverDevicesFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(discoverDevicesBroadcastReceiver, discoverDevicesFilter)

        requestPermissions()

        connectBluetooth()

        binding.connectCpmDeviceButton.setOnClickListener {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (hasBluetoothPermission() && hasFineLocationPermission() && hasCoarseLocationPermission()) {
                    if (isBluetoothEnabled) {
                        discoverDevices()
                    }
                } else {
                    Log.d(TAG, "S Android" + hasBluetoothPermission().toString())
                    Log.d(TAG, "S Android" + hasCoarseLocationPermission().toString())
                    Log.d(TAG, "S Android" + hasFineLocationPermission().toString())

                    val exitAlertDialogBuilder = AlertDialog.Builder(this@MainActivity)
                    exitAlertDialogBuilder.setTitle("Permission request")
                        .setMessage("Some permissions are denied please grant them by going to app settings.")
                        .setPositiveButton("APP SETTINGS",
                            DialogInterface.OnClickListener { dialogInterface, i ->
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                val uri = Uri.fromParts("package", packageName, null);
                                intent.data = uri;
                                startActivity(intent)
                            })
                        .setIcon(resources.getDrawable(R.drawable.ic_info, null))
                        .setCancelable(false)
                        .show()
                }

                return@setOnClickListener
            } else {
                if (hasFineLocationPermission() && hasCoarseLocationPermission()) {
                    if (isBluetoothEnabled) {
                        discoverDevices()
                    }
                } else {
                    Log.d(TAG, "Android" + hasCoarseLocationPermission().toString())
                    Log.d(TAG, "Android" + hasFineLocationPermission().toString())

                    val exitAlertDialogBuilder = AlertDialog.Builder(this@MainActivity)
                    exitAlertDialogBuilder.setTitle("Permission request")
                        .setMessage("Some permissions are denied please grant them by going to app settings.")
                        .setPositiveButton("APP SETTINGS",
                            DialogInterface.OnClickListener { dialogInterface, i ->
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                val uri = Uri.fromParts("package", packageName, null);
                                intent.data = uri;
                                startActivity(intent)
                            })
                        .setIcon(resources.getDrawable(R.drawable.ic_info, null))
                        .setCancelable(false)
                        .show()
                }
                return@setOnClickListener
            }
        }

        binding.nextButton.setOnClickListener {

            BluetoothSocketData.bluetoothSocket = bluetoothSocket
            BluetoothSocketData.initialiseOutputStream()

            if (BluetoothSocketData.isSocketConnected()) {
                Log.d(TAG, "Socket connected")
            }
            val intent = Intent(this@MainActivity, DeviceConfigurationActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "onDestroy() called")
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(discoverDevicesBroadcastReceiver)
        unregisterReceiver(bluetoothStateChangeBroadcastReceiver)
        BluetoothSocketData.close()
    }

    /**
     * Options menu related
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.close_cpm_menu -> {
                if (isHC05Connected) {
                    showRemoveCPMToCloseDialog()
                    true
                } else {
                    makeSnackbar("Please connect to CPM device")
                    true
                }
            }
            R.id.reset_cpm_menu -> {
                if (isHC05Connected) {
                    BluetoothSocketData.write("RST1")
                    showResettingAlertDialog()
                    true
                } else {
                    makeSnackbar("Please connect to CPM device")
                    true
                }
            }
            else -> {
                false
            }
        }
    }

    /**
     * Snackbars
     */
    private fun makeSnackbar(snackbarMessage: String) {
        Snackbar.make(binding.cardHome, snackbarMessage, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Alert dialog related
     */
    private fun showClosingAlertDialog() {
        closeCountDownTimerForOptionMenu.cancel()
        closeCountDownTimerForOptionMenu.start()
        val closingDialogBuilder = AlertDialog.Builder(this@MainActivity)
        closingDialogBuilder.setTitle("Please wait")
            .setMessage("Closing CPM device for 30 seconds.")
            .setIcon(resources.getDrawable(R.drawable.ic_performing, null))
            .setCancelable(false)
        closingAlertDialog = closingDialogBuilder.create()
        closingAlertDialog.show()
    }

    private fun showResettingAlertDialog() {
        resetCountDownTimerForOptionMenu.cancel()
        resetCountDownTimerForOptionMenu.start()
        val resettingDialogBuilder = AlertDialog.Builder(this@MainActivity)
        resettingDialogBuilder.setTitle("Please wait")
            .setMessage("Resetting CPM device for 30 seconds.")
            .setIcon(resources.getDrawable(R.drawable.ic_performing, null))
            .setCancelable(false)
        resettingAlertDialog = resettingDialogBuilder.create()
        resettingAlertDialog.show()
    }

    private fun showConnectionStatusDialog() {
        val connectionStatusDialogBuilder = AlertDialog.Builder(this@MainActivity)
        connectionStatusDialogBuilder.setTitle("Please Wait")
            .setMessage("Trying to connect CPM device.")
            .setIcon(resources.getDrawable(R.drawable.ic_bluetooth_searching, null))
            .setNegativeButton("CANCEL", DialogInterface.OnClickListener { dialogInterface, i ->
                connectionStatusAlertDialog.dismiss()
            })
            .setCancelable(false)

        connectionStatusAlertDialog = connectionStatusDialogBuilder.create()
        connectionStatusAlertDialog.show()
    }

    private fun showExitAlertDialog() {
        val exitAlertDialogBuilder = AlertDialog.Builder(this@MainActivity)
        exitAlertDialogBuilder.setTitle("Warning")
            .setMessage("This app wont work with bluetooth disabled.")
            .setPositiveButton("ENABLE BLUETOOTH",
                DialogInterface.OnClickListener { dialogInterface, i ->
                    connectBluetooth()
                }).setNegativeButton("EXIT", DialogInterface.OnClickListener { dialogInterface, i ->
                finish()
            }).setIcon(resources.getDrawable(R.drawable.ic_warning, null)).setCancelable(false)

        exitAlertDialog = exitAlertDialogBuilder.create()
        exitAlertDialog.show()
    }

    private fun showRemoveCPMToCloseDialog() {
        val removeCPMDialogBuilder = AlertDialog.Builder(this@MainActivity)
        removeCPMDialogBuilder.setTitle("Warning")
            .setMessage("Please remove CPM Device before closing it.")
            .setPositiveButton("CLOSE CPM",
                DialogInterface.OnClickListener { dialogInterface, i ->
                    BluetoothSocketData.write("CLS")
                    showClosingAlertDialog()
                })
            .setNegativeButton("DISMISS", DialogInterface.OnClickListener { dialogInterface, i ->

            })
            .setIcon(resources.getDrawable(R.drawable.ic_warning, null))
            .setCancelable(false)
            .show()
    }

    /**
     * Countdown timers
     */
    private val closeCountDownTimerForOptionMenu = object : CountDownTimer(30000, 1) {
        override fun onTick(p0: Long) {
        }

        override fun onFinish() {
            closingAlertDialog.dismiss()
            this.cancel()
        }
    }

    private val resetCountDownTimerForOptionMenu = object : CountDownTimer(30000, 1) {
        override fun onTick(p0: Long) {
        }

        override fun onFinish() {
            resettingAlertDialog.dismiss()
            this.cancel()
        }
    }

    /**
     * UI related chaniging functions
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun changeBluetoothStatusTextView(status: Boolean) {
        if (status) {
            runOnUiThread {
                binding.bluetoothStatusTextview.setTextColor(
                    resources.getColor(
                        R.color.green, null
                    )
                )
                binding.bluetoothStatusTextview.text = "Bluetooth status: ON"
            }
        } else {
            runOnUiThread {
                binding.bluetoothStatusTextview.setTextColor(
                    resources.getColor(
                        R.color.red, null
                    )
                )
                binding.bluetoothStatusTextview.text = "Bluetooth status: OFF"
                changeCPMDeviceStatusTextView(false)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun changeCPMDeviceStatusTextView(status: Boolean) {
        if (status) {
            runOnUiThread {
                binding.passcodeInfoTextview.visibility = View.GONE
                binding.nextButton.visibility = View.VISIBLE
                binding.connectCpmDeviceButton.visibility = View.INVISIBLE
                binding.cpmDeviceStatusTextview.setTextColor(
                    resources.getColor(
                        R.color.green, null
                    )
                )
                binding.cpmDeviceStatusTextview.text = "CPM device: Connected"
                isHC05Connected = true
                BluetoothSocketData.bluetoothSocket = bluetoothSocket
                BluetoothSocketData.initialiseOutputStream()
            }
        } else {
            runOnUiThread {
                binding.passcodeInfoTextview.visibility = View.VISIBLE
                binding.nextButton.visibility = View.GONE
                binding.connectCpmDeviceButton.visibility = View.VISIBLE
                binding.cpmDeviceStatusTextview.setTextColor(
                    resources.getColor(
                        R.color.red, null
                    )
                )
                binding.cpmDeviceStatusTextview.text = "CPM device: Not connected"
            }
        }
    }

    /**
     * Handling back key pressed
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            exitByBackKey()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun exitByBackKey() {
        AlertDialog.Builder(this)
            .setTitle("Exit")
            .setIcon(resources.getDrawable(R.drawable.ic_info, null))
            .setMessage("Do you really want to close the app?")
            .setPositiveButton("DISMISS") { arg0, arg1 ->

            }
            .setNegativeButton("EXIT", DialogInterface.OnClickListener { dialogInterface, i ->
                finish()
            })
            .show()
    }
}