package com.aaas.cpmcontroller

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.aaas.cpmcontroller.databinding.ActivityExerciseCompleteBinding

class ExerciseCompleteActivity : AppCompatActivity() {

    private val TAG = ExerciseCompleteActivity::class.java.simpleName

    private lateinit var binding: ActivityExerciseCompleteBinding

    private lateinit var closingAlertDialog: AlertDialog
    private lateinit var resettingAlertDialog: AlertDialog

    /**
     * Bluetooth related things
     * and Broadcast Receivers
     */
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

    /**
     * Lifecycle related
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseCompleteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bluetoothActionStateIntentFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateChangeBroadcastReceiver, bluetoothActionStateIntentFilter)

        val connectionStatusFilter = IntentFilter()
        connectionStatusFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        connectionStatusFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        connectionStatusFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(connectionStatusReceiver, connectionStatusFilter)

        val angleIntentString = intent.getStringExtra("angle")
        val repetitionsIntentString = intent.getStringExtra("repetitions")

        binding.angleRepetitionsInfoTextview.text =
            "$repetitionsIntentString repetitions at $angleIntentString\u00B0"

        binding.exerciseAgainButton.setOnClickListener {
            finish()
        }

        binding.closeAppButton.setOnClickListener {
            BluetoothSocketData.write("CLS")
            showClosingAlertDialog()
            closeCountDownTimerForOptionMenu.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(bluetoothStateChangeBroadcastReceiver)
        unregisterReceiver(connectionStatusReceiver)
    }

    /**
     * Alert dialog related
     */
    private fun showDisconnectAlertDialog() {
        val retryAlertDialogBuilder = AlertDialog.Builder(this@ExerciseCompleteActivity)
        retryAlertDialogBuilder
            .setTitle("Warning")
            .setMessage("Unfortunately the CPM device got disconnected. Please reconnect and restart the process.")
            .setPositiveButton("RESTART", DialogInterface.OnClickListener { dialogInterface, i ->
                finish()
            })
            .setIcon(resources.getDrawable(R.drawable.ic_warning, null))
            .setCancelable(false)
            .show()
    }

    private fun showExitAlertDialog() {
        val exitAlertDialogBuilder = AlertDialog.Builder(this@ExerciseCompleteActivity)
        exitAlertDialogBuilder.setTitle("Warning")
            .setMessage("Unfortunately or by mistake the bluetooth is disabled, CPM device will now reset. Restart the app and re-connect and re-configure the CPM device.")
            .setNegativeButton("RESTART", DialogInterface.OnClickListener { dialogInterface, i ->
                finish()
            })
            .setIcon(resources.getDrawable(R.drawable.ic_warning, null))
            .setCancelable(false)
            .show()
    }

    private fun showClosingAlertDialog() {
        closeCountDownTimerForOptionMenu.cancel()
        closeCountDownTimerForOptionMenu.start()
        val retryAlertDialogBuilder = AlertDialog.Builder(this@ExerciseCompleteActivity)
        retryAlertDialogBuilder.setTitle("Please wait")
            .setMessage("Closing CPM device for 30 seconds. After this the app will be closed automatically.")
            .setIcon(resources.getDrawable(R.drawable.ic_performing, null))
            .setCancelable(false)

        closingAlertDialog = retryAlertDialogBuilder.create()
        closingAlertDialog.show()
    }

    private fun showResettingAlertDialog() {
        resetCountDownTimerForOptionMenu.cancel()
        resetCountDownTimerForOptionMenu.start()
        val resettingDialogBuilder = AlertDialog.Builder(this@ExerciseCompleteActivity)
        resettingDialogBuilder.setTitle("Please wait")
            .setMessage("Resetting CPM device for 30 seconds.")
            .setIcon(resources.getDrawable(R.drawable.ic_performing, null))
            .setCancelable(false)
        resettingAlertDialog = resettingDialogBuilder.create()
        resettingAlertDialog.show()
    }

    /**
     * Countdown timers
     */
    private val closeCountDownTimerForOptionMenu = object : CountDownTimer(30000, 1) {
        override fun onTick(p0: Long) {
        }

        override fun onFinish() {
            closingAlertDialog.dismiss()
            BluetoothSocketData.close()
            finish()
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            startActivity(intent)
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
     * Options menu related
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.close_cpm_menu -> {
                BluetoothSocketData.write("CLS")
                showClosingAlertDialog()
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
}