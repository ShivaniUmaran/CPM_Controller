package com.aaas.cpmcontroller

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.aaas.cpmcontroller.databinding.ActivityDeviceControlBinding
import java.io.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class DeviceControlActivity : AppCompatActivity() {

    private val TAG: String = DeviceControlActivity::class.java.simpleName

    private lateinit var exitAlertDialog: AlertDialog

    private lateinit var binding: ActivityDeviceControlBinding

    private var repetitionsIntentString: String? = null
    private var angleIntentString: String? = null

    private lateinit var resettingAlertDialog: AlertDialog
    private lateinit var closingAlertDialog: AlertDialog

    /**
     * Bluetooth related things
     * and Broadcast Receivers
     */
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
                            Log.d(TAG, "onReceive: STATE OFF")
                        }
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            Log.d(TAG, "bluetoothStateChangeBroadcastReceiver: STATE TURNING OFF")
                            showExitAlertDialog()
                        }
                    }
                }
            }
        }

    private val connectionStatusReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission", "NewApi")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action.toString()) {

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(TAG, "ACTION_ACL_CONNECTED")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED -> {
                    Log.d(TAG, "ACTION_ACL_DISCONNECT_REQUESTED")
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d(TAG, "ACTION_ACL_DISCONNECTED")
                    showDisconnectAlertDialog()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ReadThread : Thread() {

        override fun run() {
            super.run()
            val handler: Handler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    super.handleMessage(msg)

                    when (msg.what) {
                        BluetoothDataHandlerConstant.DATA_REPETITIONS_DONE -> {
                            val readBuf = msg.obj as ByteArray
                            // construct a string from the valid bytes in the buffer
                            val readMessage = String(readBuf, 0, msg.arg1)
                            if (checkForDigit(readMessage)) {
                                runOnUiThread {
                                    val inputNoOfRepetitions =
                                        Integer.parseInt(repetitionsIntentString.toString())
                                    val repetitionsLeftFromBuffer =
                                        Integer.parseInt(readMessage)
                                    binding.angleRepetitionsInfoTextview.text =
                                        "CPM Device is performing and\n${inputNoOfRepetitions - repetitionsLeftFromBuffer} repetitions left at $angleIntentString\u00B0"

                                    if (inputNoOfRepetitions - repetitionsLeftFromBuffer == 0) {

                                        finish()

                                        val intent = Intent(
                                            this@DeviceControlActivity,
                                            ExerciseCompleteActivity::class.java
                                        )
                                        intent.putExtra("repetitions", repetitionsIntentString)
                                        intent.putExtra("angle", angleIntentString)
                                        startActivity(intent)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            BluetoothSocketData.mHandler = handler
            BluetoothSocketData.read()
        }

        private fun checkForDigit(string: String): Boolean {
            val regex = "[0-9]+"
            val p: Pattern = Pattern.compile(regex)
            val m: Matcher = p.matcher(string)
            return m.matches()
        }
    }

    /**
     * Lifecycle related
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Control your CPM device"

        val bluetoothActionStateIntentFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateChangeBroadcastReceiver, bluetoothActionStateIntentFilter)

        val connectionStatusFilter = IntentFilter()
        connectionStatusFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        connectionStatusFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        connectionStatusFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(connectionStatusReceiver, connectionStatusFilter)

        angleIntentString = intent.getStringExtra("angle")
        repetitionsIntentString = (intent.getIntExtra("repetitions", 0)).toString()

        Log.d(TAG, "Input Angle: $angleIntentString")

        ReadThread().start()

        binding.resetCpmButton.setOnClickListener {
            BluetoothSocketData.write("RST")
        }

        binding.stopCpmButton.setOnClickListener {
            BluetoothSocketData.write("STP")
            runOnUiThread {
                binding.stopCpmButton.visibility = View.GONE
                binding.resumeCpmButton.visibility = View.VISIBLE
            }
        }

        binding.resumeCpmButton.setOnClickListener {
            BluetoothSocketData.write("RES")
            runOnUiThread {
                binding.resumeCpmButton.visibility = View.GONE
                binding.stopCpmButton.visibility = View.VISIBLE
            }
        }

        binding.angleRepetitionsInfoTextview.text =
            "CPM Device is performing and\n$repetitionsIntentString repetitions left at $angleIntentString\u00B0"

        val rotate = RotateAnimation(
            360F, 0F,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        rotate.duration = 1000
        rotate.repeatCount = Animation.INFINITE
        binding.rotateIconImageview.startAnimation(rotate)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateChangeBroadcastReceiver)
        unregisterReceiver(connectionStatusReceiver)
        Log.d(TAG, "onDestroy() called")
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
                showRemoveCPMToCloseDialog()
                true
            }
            R.id.reset_cpm_menu -> {
                BluetoothSocketData.write("RST1")
                showResettingAlertDialog()
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Alert dialog related
     */
    private fun showClosingAlertDialog() {
        closeCountDownTimerForOptionMenu.cancel()
        closeCountDownTimerForOptionMenu.start()
        val closingDialogBuilder = AlertDialog.Builder(this@DeviceControlActivity)
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
        val resettingDialogBuilder = AlertDialog.Builder(this@DeviceControlActivity)
        resettingDialogBuilder.setTitle("Please wait")
            .setMessage("Resetting CPM device for 30 seconds.")
            .setIcon(resources.getDrawable(R.drawable.ic_performing, null))
            .setCancelable(false)
        resettingAlertDialog = resettingDialogBuilder.create()
        resettingAlertDialog.show()
    }

    private fun showExitAlertDialog() {
        val exitAlertDialogBuilder = AlertDialog.Builder(this@DeviceControlActivity)
        exitAlertDialogBuilder.setTitle("Warning")
            .setMessage("Unfortunately or by mistake the bluetooth is disabled, CPM device will now reset. Restart the app and re-connect and re-configure the CPM device.")
            .setNegativeButton("RESTART", DialogInterface.OnClickListener { dialogInterface, i ->
                finish()
            })
            .setIcon(resources.getDrawable(R.drawable.ic_warning, null))
            .setCancelable(false)

        exitAlertDialog = exitAlertDialogBuilder.create()
        exitAlertDialog.show()
    }

    private fun showDisconnectAlertDialog() {
        val retryAlertDialogBuilder = AlertDialog.Builder(this@DeviceControlActivity)
        retryAlertDialogBuilder
            .setTitle("Warning")
            .setMessage("Unfortunately the CPM device got disconnected. Please reconnect and restart the process.")
            .setPositiveButton("RESTART", DialogInterface.OnClickListener { dialogInterface, i ->
                BluetoothSocketData.write("RST")
                finish()
            })
            .setIcon(resources.getDrawable(R.drawable.ic_warning, null))
            .setCancelable(false)
            .show()
    }

    private fun showRemoveCPMToCloseDialog() {
        val removeCPMDialogBuilder = AlertDialog.Builder(this@DeviceControlActivity)
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
            .setTitle("Do you want to restart the process?")
            .setIcon(resources.getDrawable(R.drawable.ic_warning, null))
            .setPositiveButton("DISMISS") { arg0, arg1 ->

            }
            .setNegativeButton("RESTART", DialogInterface.OnClickListener { dialogInterface, i ->
                finish()
            })
            .setCancelable(false)
            .show()
    }
}