package redstone.footsensor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import redstone.footsensor.databinding.ActivityDashboardBinding
import java.util.UUID

private const val SERVICE_UUID = "00002333-0000-1000-8000-00805F9B34FB"
private const val CHAR_UUID_TX = "6E400002-B5A3-F393-E0A9-114514191981"
private const val CHAR_UUID_RX = "6E400003-B5A3-F393-E0A9-114514191981"
private const val CHAR_CFG_UUID = "00002902-0000-1000-8000-00805f9b34fb"

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    private lateinit var sensorListAdapter: SensorListAdapter

    private fun checkPermission(permission: String) =
        ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!(checkPermission(Manifest.permission.BLUETOOTH) &&
                    checkPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    checkPermission(Manifest.permission.BLUETOOTH_CONNECT))
        ) {
            Log.i("BLE", "Request permission")
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), 114514
            )
        }

        val sensorList = binding.sensorList
        val buttonDisconnect = binding.buttonDisconnect

        val btManager = getSystemService(BluetoothManager::class.java)
        val btAdapter = btManager.adapter
        if (btAdapter == null)
            Snackbar.make(binding.root, "Your shit doesn't support BT!", 1000).show()


        if (!btAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val code = it.resultCode
                if (code == RESULT_OK)
                    Snackbar.make(binding.root, "OK", 1000).show()
                else
                    Snackbar.make(binding.root, "Fuck you!", 1000).show()

            }.launch(enableBtIntent)
        }

        val address = intent.getStringExtra("address")
        try {
            val device = btAdapter.getRemoteDevice(address)
            device.connectGatt(this, true, gattCallback)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Can't connect", Toast.LENGTH_SHORT).show()
            finish()
        }

        buttonDisconnect.setOnClickListener {
            finish()
        }

        sensorListAdapter = SensorListAdapter()
        sensorList.adapter = sensorListAdapter

    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            114514 -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty()) {
                    grantResults.forEach {
                        if (it != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "Fuck you!", Toast.LENGTH_SHORT).show()
                            finish()
                            return
                        }
                    }
                    Toast.makeText(this, "OK", Toast.LENGTH_SHORT).show()
                }
                return
            }
            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS)
                Log.e("BLE", "Fucked: $status")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (gatt == null)
                    return

                Log.i("BLE", "Connected")
                mainExecutor.execute {
                    binding.sensorList.visibility = View.VISIBLE
                    binding.buttonDisconnect.setOnClickListener {
                        gatt.close()
                        //Shitty thing
                        this.onConnectionStateChange(gatt, 0, BluetoothGatt.STATE_DISCONNECTED)
                        finish()
                    }
                    Toast.makeText(this@DashboardActivity, "Connected", Toast.LENGTH_SHORT).show()
                }
                gatt.discoverServices()
            } else {
                Log.i("BLE", "Disconnected")
                mainExecutor.execute {
                    Toast.makeText(this@DashboardActivity, "Disconnected", Toast.LENGTH_SHORT)
                        .show()
                    binding.sensorList.visibility = View.GONE
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            gatt?.services?.forEach { Log.i("BLE", "Service: ${it.uuid}") }
            try {
                val service = gatt?.getService(UUID.fromString(SERVICE_UUID))
                if (service == null) {
                    Log.e("BLE", "Fucked getting service")
                    return
                }
                val char = service.getCharacteristic(UUID.fromString(CHAR_UUID_RX))

                // The new API is available only in API 33 or later
                @Suppress("DEPRECATION")
                binding.buttonSub.setOnClickListener {
                    it as Button

                    val desc = char.getDescriptor(UUID.fromString(CHAR_CFG_UUID))
                    if (it.text == resources.getString(R.string.action_sub)) {
                        it.text = resources.getString(R.string.action_un_sub)

                        gatt.setCharacteristicNotification(char, true)
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        it.text = resources.getString(R.string.action_sub)

                        gatt.setCharacteristicNotification(char, false)
                        desc.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    }
                    gatt.writeDescriptor(desc)

                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DashboardActivity, "WTF is the device?", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val sensorData = Array(3) { Array<UByte>(4) { 0u } }
            for (i in 0 until 12) {
                sensorData[i / 4][i % 4] = value[i].toUByte()
            }

            runOnUiThread {
                sensorListAdapter.updateData(sensorData)
            }
        }
    }
}

private class SensorListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val sensor1: TextView = itemView.findViewById(R.id.text_sensor1)
    private val sensor2: TextView = itemView.findViewById(R.id.text_sensor2)
    private val sensor3: TextView = itemView.findViewById(R.id.text_sensor3)
    private val sensor4: TextView = itemView.findViewById(R.id.text_sensor4)

    val sensors = arrayOf(sensor1, sensor2, sensor3, sensor4)
}

@SuppressLint("MissingPermission")
private class SensorListAdapter : RecyclerView.Adapter<SensorListViewHolder>() {

    private var sensorValues = Array(3) { arrayOf<UByte>(114U, 5U, 14U, 191U) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorListViewHolder =
        SensorListViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.sensors_item_view, parent, false)
        )

    override fun getItemCount() = 3

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: SensorListViewHolder, position: Int) {
        for (sensorID in 0 until 4) {
            val textBg = GradientDrawable().apply {
                cornerRadius = 20F
                setColor(Color.argb(sensorValues[position][sensorID].toInt(), 0xE5, 0x39, 0x35))
            }
            holder.sensors[sensorID].background = textBg
            holder.sensors[sensorID].text = "$sensorID, $position"
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(data: Array<Array<UByte>>) {
        sensorValues = data
        notifyDataSetChanged()
    }
}
