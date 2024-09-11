package com.aaas.cpmcontroller

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.aaas.cpmcontroller.databinding.ActivityDeviceConfigurationBinding
import com.google.android.material.snackbar.Snackbar

class DeviceConfigurationActivity : AppCompatActivity() {

    private val TAG = DeviceConfigurationActivity::class.java.simpleName

    private lateinit var resettingAlertDialog: AlertDialog
    private lateinit var closingAlertDialog: AlertDialog
    private lateinit var exitAlertDialog: AlertDialog

    private var isHC05Paired = false

    private lateinit var binding: ActivityDeviceConfigurationBinding

    /**
     * TextWatchers for edit texts
     */
    private val angleEditTextTextWatcher: TextWatcher by lazy {
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                binding.angleTextInputLayout.error = null

                var enteredAngle = 0
                if (binding.angleEditText.text.toString().isNotEmpty()) {
                    enteredAngle = binding.angleEditText.text.toString().toInt()
                }
                if (enteredAngle < 1) {
                    binding.angleEditText.setText("1")
                    makeSnackbar("Minimum angle can be 1\u00B0")
                }
                if (enteredAngle > 120) {
                    binding.angleEditText.setText("120")
                    makeSnackbar("Maximum angle can be 120\u00B0")
                }
            }

            override fun afterTextChanged(s: Editable) {}
        }
    }

    private val repetitionsETTextWatcher: TextWatcher by lazy {
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                binding.repetitionsTextInputLayout.error = null

                var enteredRepetitions = 0
                if (binding.repetitionsEditText.text.toString().isNotEmpty()) {
                    enteredRepetitions = binding.repetitionsEditText.text.toString().toInt()
                }
                if (enteredRepetitions < 0) {
                    binding.repetitionsEditText.setText("0")
                    makeSnackbar("Minimum repetitions can be 1")
                }
                if (enteredRepetitions > 100) {
                    binding.repetitionsEditText.setText("100")
                    makeSnackbar("Maximum repetitions can be 100")
                }
            }

            override fun afterTextChanged(s: Editable) {}
        }
    }

    /**
     * Initialising input edit texts
     */
    private fun initializeAngleInputEditText() {

        binding.angleEditText.addTextChangedListener(angleEditTextTextWatcher)

        binding.plusAngleButton.setOnClickListener {
            var enteredEditTextAngle = 0
            if (binding.angleEditText.text.toString().isNotEmpty()) {
                enteredEditTextAngle = binding.angleEditText.text.toString().toInt()
                enteredEditTextAngle += 1
                if (enteredEditTextAngle > 120) {
                    binding.angleEditText.setText("120")
                }
                binding.angleEditText.setText(enteredEditTextAngle.toString())
            } else {
                enteredEditTextAngle += 1
                binding.angleEditText.setText(enteredEditTextAngle.toString())
            }
        }

        binding.minusAngleButton.setOnClickListener {
            if (binding.angleEditText.text.toString().isNotEmpty()) {
                var enteredEditTextAngle: Int = binding.angleEditText.text.toString().toInt()
                enteredEditTextAngle -= 1
                if (enteredEditTextAngle < 1) {
                    binding.angleEditText.setText("1")
                }
                binding.angleEditText.setText(enteredEditTextAngle.toString())
            }
        }

        binding.degree30Button.setOnClickListener {
            binding.angleEditText.setText("30")
        }

        binding.degree45Button.setOnClickListener {
            binding.angleEditText.setText("45")
        }

        binding.degree60Button.setOnClickListener {
            binding.angleEditText.setText("60")
        }

        binding.degree75Button.setOnClickListener {
            binding.angleEditText.setText("75")
        }

        binding.degree90Button.setOnClickListener {
            binding.angleEditText.setText("90")
        }

        binding.degree120Button.setOnClickListener {
            binding.angleEditText.setText("120")
        }
    }

    private fun initializeRepetitionsInputEditText() {

        binding.repetitionsEditText.addTextChangedListener(repetitionsETTextWatcher)

        binding.tenRepetitionsButton.setOnClickListener {
            binding.repetitionsEditText.setText("10")
        }

        binding.twentyRepetitionsButton.setOnClickListener {
            binding.repetitionsEditText.setText("20")
        }

        binding.thirtyRepetitionsButton.setOnClickListener {
            binding.repetitionsEditText.setText("30")
        }

        binding.fourtyRepetitionsButton.setOnClickListener {
            binding.repetitionsEditText.setText("40")
        }

        binding.fiftyRepetitionsButton.setOnClickListener {
            binding.repetitionsEditText.setText("50")
        }
    }

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
                        BluetoothAdapter.STATE_ON -> {
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

    private val connectionStatusReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission", "NewApi")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action.toString()) {

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(TAG, "ACTION_ACL_CONNECTED")
                    isHC05Paired = true
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

    /**
     * Lifecycle related
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceConfigurationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bluetoothActionStateIntentFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateChangeBroadcastReceiver, bluetoothActionStateIntentFilter)

        val connectionStatusFilter = IntentFilter()
        connectionStatusFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        connectionStatusFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        connectionStatusFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(connectionStatusReceiver, connectionStatusFilter)

        showResettingAlertDialog()
        resetCPMCountDownTimer.start()

        supportActionBar?.title = "Configure CPM Device"

        initializeAngleInputEditText()

        initializeRepetitionsInputEditText()

        binding.startDeviceButton.setOnClickListener {
            if (binding.angleEditText.text.toString().isEmpty()) {
                binding.angleTextInputLayout.error = "This field is empty"
            } else if (binding.repetitionsEditText.text.toString().isEmpty()) {
                binding.repetitionsTextInputLayout.error = "This field is empty"
            } else {
                val angleInputInteger = Integer.parseInt(binding.angleEditText.text.toString())
                val angleInRadians = (Math.PI / 180) * angleInputInteger

                val L: Double = 57.22 + 42.78 * Math.cos(angleInRadians)
                Log.d(TAG, "L: $L")

                val l: Double = Math.ceil(5 * Math.sqrt(L))
                Log.d(TAG, "l: $l")

                // val x: Double = (l - 30)
                val x: Double = (50 - l)
                Log.d(TAG, "x: $x")

                val delay = ((x / 7) * 10000).toInt()
                // ((Math.sqrt(1069.5 * Math.cos(Math.PI - angleInRadians) + 1190.25 + 240.25) - 30) / 7) * 10000
                Log.d(TAG, "Delay: $delay Integer: ${delay.toInt()}")

                val repetitionsInInteger: Int =
                    Integer.parseInt(binding.repetitionsEditText.text.toString())
                BluetoothSocketData.write("$delay,$repetitionsInInteger")

                unregisterReceiver(bluetoothStateChangeBroadcastReceiver)
                unregisterReceiver(connectionStatusReceiver)

                finish()

                val intent =
                    Intent(this@DeviceConfigurationActivity, DeviceControlActivity::class.java)
                intent.putExtra("angle", binding.angleEditText.text.toString())
                intent.putExtra("repetitions", repetitionsInInteger)
                startActivity(intent)
            }
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
                showRemoveCPMToCloseDialog()
                true
            }
            R.id.reset_cpm_menu -> {
                BluetoothSocketData.write("RST1")
                showResettingAlertDialogForOptionsMenu()
                true
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
        Snackbar.make(binding.startDeviceButton, snackbarMessage, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Alert dialog related
     */
    private fun showClosingAlertDialog() {
        closeCountDownTimerForOptionMenu.cancel()
        closeCountDownTimerForOptionMenu.start()
        val closingDialogBuilder = AlertDialog.Builder(this@DeviceConfigurationActivity)
        closingDialogBuilder.setTitle("Please wait")
            .setMessage("Closing CPM device for 30 seconds.")
            .setIcon(resources.getDrawable(R.drawable.ic_performing, null))
            .setCancelable(false)
        closingAlertDialog = closingDialogBuilder.create()
        closingAlertDialog.show()
    }

    private fun showDisconnectAlertDialog() {
        val retryAlertDialogBuilder = AlertDialog.Builder(this@DeviceConfigurationActivity)
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

    private fun showResettingAlertDialogForOptionsMenu() {
        resetCountDownTimerForOptionMenu.cancel()
        resetCountDownTimerForOptionMenu.start()
        val retryAlertDialogBuilder = AlertDialog.Builder(this@DeviceConfigurationActivity)
        retryAlertDialogBuilder.setTitle("Please wait")
            .setMessage("Resetting CPM device for 30 seconds.")
            .setIcon(resources.getDrawable(R.drawable.ic_performing, null))
            .setCancelable(false)
        resettingAlertDialog = retryAlertDialogBuilder.create()
        resettingAlertDialog.show()
    }

    private fun showResettingAlertDialog() {
        BluetoothSocketData.write("RST1")
        val retryAlertDialogBuilder = AlertDialog.Builder(this@DeviceConfigurationActivity)
        retryAlertDialogBuilder.setTitle("Please wait")
            .setMessage("Resetting CPM device for 30 seconds.")
            .setIcon(resources.getDrawable(R.drawable.ic_performing, null))
            .setCancelable(false)
        resettingAlertDialog = retryAlertDialogBuilder.create()
        resettingAlertDialog.show()
    }

    private fun showExitAlertDialog() {
        val exitAlertDialogBuilder = AlertDialog.Builder(this@DeviceConfigurationActivity)
        exitAlertDialogBuilder.setTitle("Warning")
            .setMessage("Unfortunately or by mistake the bluetooth is disabled. Restart the app and re-connect and re-configure the CPM device.")
            .setNegativeButton("EXIT", DialogInterface.OnClickListener { dialogInterface, i ->
                finish()
            })
            .setIcon(resources.getDrawable(R.drawable.ic_warning, null))
            .setCancelable(false)

        exitAlertDialog = exitAlertDialogBuilder.create()
        exitAlertDialog.show()
    }

    private fun showRemoveCPMToCloseDialog() {
        val removeCPMDialogBuilder = AlertDialog.Builder(this@DeviceConfigurationActivity)
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

    private val resetCPMCountDownTimer = object : CountDownTimer(30000, 1) {
        override fun onTick(p0: Long) {
        }

        override fun onFinish() {
            this.cancel()
            resettingAlertDialog?.dismiss()

            val cpmReadySnackbar = Snackbar.make(
                binding.startDeviceButton,
                "CPM device ready. You can now wear it.",
                Snackbar.LENGTH_INDEFINITE
            )
            cpmReadySnackbar.setAction("OKAY", View.OnClickListener {
                fun onClick(v: View) {
                    // Code to undo the user's last action
                    cpmReadySnackbar.dismiss()
                }
            })
            cpmReadySnackbar.show()
        }
    }
}