package redstone.footsensor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
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
import redstone.footsensor.databinding.ActivityMainBinding

private const val SERVICE_UUID = "00002333-0000-1000-8000-00805F9B34FB"

class MainActivity : AppCompatActivity() {

    var gattConnected: BluetoothGatt? = null
    private lateinit var binding: ActivityMainBinding

    private fun checkPermission(permission: String) =
        ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
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

        val rootLayout = binding.rootLayout
        val deviceList = binding.deviceList
        val refreshLayout = binding.refreshLayout

        val btManager = getSystemService(BluetoothManager::class.java)
        val btAdapter = btManager.adapter
        if (btAdapter == null)
            Snackbar.make(rootLayout, "Your shit doesn't support BT!", 1000).show()


        if (!btAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val code = it.resultCode
                if (code == RESULT_OK)
                    Snackbar.make(rootLayout, "OK", 1000).show()
                else
                    Snackbar.make(rootLayout, "Fuck you!", 1000).show()

            }.launch(enableBtIntent)
        }
        val devListAdapter = DeviceListAdapter(this)
        deviceList.adapter = devListAdapter

        val leScanCallback: ScanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                devListAdapter.addDevice(result.device)

                Log.i("BLE", "New device: ${result.device.name}")
            }
        }


        refreshLayout.setOnRefreshListener {
            if (!btAdapter.isEnabled) {
                Snackbar.make(rootLayout, "Bluetooth shut!", 1000).show()
                refreshLayout.finishRefresh(false)
                return@setOnRefreshListener
            }
            devListAdapter.clearList()

            val bleScanner = btAdapter.bluetoothLeScanner

            Handler(Looper.getMainLooper()).postDelayed({
                // Stop scanning after 5s
                bleScanner.stopScan(leScanCallback)

                runOnUiThread { refreshLayout.finishRefresh(true) }
            }, 5000)

            val filter = ScanFilter.Builder()
                .setServiceUuid(
                    ParcelUuid.fromString(SERVICE_UUID),
                    ParcelUuid.fromString("FFFFFFFF-FFFF-FFFF-FFFF-000000000000"),
                )
                .build()
            val settings = ScanSettings.Builder().build()
            bleScanner.startScan(listOf(filter), settings, leScanCallback)
        }


        refreshLayout.autoRefresh()
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
}

private class DeviceListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val textDeviceName: TextView = itemView.findViewById(R.id.text_device_name)
    val buttonConnect: Button = itemView.findViewById(R.id.button_connect)

}

@SuppressLint("MissingPermission")
private class DeviceListAdapter(
    private val context: Context
) :
    RecyclerView.Adapter<DeviceListViewHolder>() {
    private val devices = mutableListOf<BluetoothDevice>()
    fun addDevice(dev: BluetoothDevice) {
        // Avoid duplication
        if (dev in devices)
            return

        devices.add(dev)
        this.notifyItemInserted(devices.size)
    }

    fun clearList() {
        this.notifyItemRangeRemoved(0, devices.size)
        devices.clear()
    }

    override fun getItemCount() = devices.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceListViewHolder =
        DeviceListViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.devices_item_view, parent, false)
        )


    fun startDashboardActivity(address: String) {
        val intent = Intent()
        intent.setClass(context, DashboardActivity::class.java)
        intent.putExtra("address", address)
        context.startActivity(intent)
    }

    override fun onBindViewHolder(holder: DeviceListViewHolder, position: Int) {
        holder.textDeviceName.text = devices[position].name
        holder.buttonConnect.setOnClickListener {
            startDashboardActivity(devices[position].address)
        }
    }

}


