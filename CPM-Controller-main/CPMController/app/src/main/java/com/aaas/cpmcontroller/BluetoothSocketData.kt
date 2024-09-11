package com.aaas.cpmcontroller

import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.util.Log
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BluetoothSocketData {

    companion object {
        private val TAG = BluetoothSocketData::class.java.simpleName
        var mHandler: Handler? = null
        var bluetoothSocket: BluetoothSocket? = null
        private var mBuffer: String? = null
        private lateinit var mOutputStream: OutputStream

        fun isSocketConnected(): Boolean {
            mOutputStream = bluetoothSocket?.outputStream!!
            return bluetoothSocket?.isConnected!!
        }

        fun initialiseOutputStream() {
            try {
                mOutputStream = bluetoothSocket?.outputStream!!
            } catch (e: Exception) {

            }
        }

        fun read() {
            try {
                val tmpIn: InputStream? = bluetoothSocket?.inputStream
                val mmInStream = DataInputStream(tmpIn)
                var mmBuffer: ByteArray
                var numBytes: Int
                while (true) {
                    mmBuffer = ByteArray(1024)
                    // Read from the InputStream.
                    numBytes = try {
                        mmInStream.read(mmBuffer)
                    } catch (e: IOException) {
                        Log.d(TAG, "Input stream was disconnected", e)
                        break
                    }

                    val readMsg = mHandler?.obtainMessage(
                        BluetoothDataHandlerConstant.DATA_REPETITIONS_DONE,
                        numBytes,
                        -1,
                        mmBuffer
                    )
                    readMsg?.sendToTarget()
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }

        fun write(bytes: String) {
            mBuffer = bytes
            try {
                mOutputStream = bluetoothSocket?.outputStream!!
                Log.d(TAG, mBuffer!!)
                mOutputStream?.write(mBuffer!!.toByteArray())
            } catch (e: Exception) {
                Log.d(TAG, "mOutputStream.write() failed - Failed to write")
            }
        }

        fun close() {
            try {
                bluetoothSocket?.close()
                Log.d(TAG, "Bluetooth socket closed")
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }
}