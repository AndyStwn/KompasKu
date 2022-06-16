package com.andysetyawan.kompasku

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.andysetyawan.kompasku.databinding.ActivityMainBinding
import com.andysetyawan.kompasku.model.InternalFileRepository
import com.andysetyawan.kompasku.model.Note
import com.andysetyawan.kompasku.model.NoteRepository
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_ACCELEROMETER
import android.hardware.Sensor.TYPE_MAGNETIC_FIELD
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_GAME
import android.view.animation.Animation.RELATIVE_TO_SELF
import android.view.animation.RotateAnimation
import android.widget.ImageView
import java.lang.Math.toDegrees


class MainActivity : AppCompatActivity() , SensorEventListener{

    private val repo: NoteRepository by lazy { InternalFileRepository(this) }
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationRequest: LocationRequest
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var datagps: TextView
    private lateinit var datalatlong: TextView
    private val PermissionCode = 2
    lateinit var sensorManager: SensorManager
    lateinit var image: ImageView
    lateinit var accelerometer: Sensor
    lateinit var magnetometer: Sensor

    var currentDegree = 0.0f
    var lastAccelerometer = FloatArray(3)
    var lastMagnetometer = FloatArray(3)
    var lastAccelerometerSet = false
    var lastMagnetometerSet = false

    //Membuat objek binding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        image = findViewById(R.id.compas) as ImageView
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        //inisialisasi data
        binding.getgps.setOnClickListener {
            cekijinlokasi()
        }

        //memasukan data sensor gps ke file
        binding.log.setOnClickListener {
            var logDataSensor = binding.editTeksCatatan.text.toString()
            val timeStamp: String = SimpleDateFormat("yy-MM-dd").format(Date())
            binding.editFileName.setText("Posisiku-" + timeStamp + ".txt")
            val alamat = binding.textView.text.toString()
            val latitudelongitud = binding.LatLong.text.toString()
            logDataSensor = "$logDataSensor$alamat , $latitudelongitud\n"
            binding.editTeksCatatan.setText(logDataSensor)
        }

        //menambahkan tombol tulis
        //untuk menyimpan file
        binding.buat.setOnClickListener {
            if (binding.editFileName.text.isNotEmpty()) {
                try {
                    repo.addNote(
                        Note(
                            binding.editFileName.text.toString(),
                            binding.editTeksCatatan.text.toString()
                        )
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, "File Write Failed", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
                binding.editFileName.text.clear()
                binding.editTeksCatatan.text.clear()
            } else {
                Toast.makeText(this, "Please provide a Filename", Toast.LENGTH_LONG).show()
            }
        }

        //untuk membuka file
        binding.cari.setOnClickListener {
            if (binding.editFileName.text.isNotEmpty()) {
                try {
                    val note = repo.getNote(binding.editFileName.text.toString())
                    binding.editTeksCatatan.setText(note.noteText)
                } catch (e: Exception) {
                    Toast.makeText(this, "File Read Failed", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            } else {
                Toast.makeText(this, "Please provide a Filename", Toast.LENGTH_LONG).show()
            }
        }
        //menghapus file beserta isi
        binding.hapus.setOnClickListener {
            if (binding.editFileName.text.isNotEmpty()) {
                try {
                    if (repo.deleteNote(binding.editFileName.text.toString())) {
                        Toast.makeText(this, "File Deleted", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "File Could Not Be Deleted", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "File Delete Failed", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
                binding.editFileName.text.clear()
                binding.editTeksCatatan.text.clear()
            } else {
                Toast.makeText(this, "Please provide a Filename", Toast.LENGTH_LONG).show()
            }
        }

        //membagikan alamat ke sosmed, dll dengan intent
        binding.bagikan.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            val logData1 = binding.textView.text.toString()
            intent.putExtra(Intent.EXTRA_TEXT, logData1)
            intent.putExtra(Intent.EXTRA_SUBJECT, "Subject Here")
            val chooser = Intent.createChooser(intent, "Bagikan Dengan : ")
            startActivity(chooser)
        }
    }
    //mengecek perizinan lokasi hp
    private fun cekijinlokasi() {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            checkGPS()
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100)
        }
    }

    //mengecek GPS hp
    private fun checkGPS() {
        locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = 5000
        locationRequest.fastestInterval = 2000

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        builder.setAlwaysShow(true)
        val result = LocationServices.getSettingsClient(
            this.applicationContext
        )
            .checkLocationSettings(builder.build())

        result.addOnCompleteListener { task ->

            try {
                //ketika gps on
                val response = task.getResult(
                    ApiException::class.java
                )
                getuserLocation()
            } catch (e: ApiException) {
                //ketika gps off
                e.printStackTrace()
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        val resolveApiException = e as ResolvableApiException
                        resolveApiException.startResolutionForResult(this, 200)
                    } catch (sendIntentException: IntentSender.SendIntentException) {

                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {

                    }

                }
            }

        }
    }

    //untuk mendapatkan lokasi terkini
    private fun getuserLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // di sini untuk meminta izin yang hilang, dan kemudian menimpa
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // untuk menangani kasus di mana pengguna memberikan izin.
            // untuk ActivityCompat#requestPermissions untuk detail selengkapnya.
            return
        }
        fusedLocationProviderClient.lastLocation.addOnCompleteListener { task ->

            val location = task.getResult()
            if (location != null) {
                try {
                    datagps = findViewById(R.id.textView)
                    val geocoder = Geocoder(this, Locale.getDefault())
                    val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    val address_line = address[0].getAddressLine(0)
                    //menampilkan alamat geocoder pada textview
                    datagps.text = "" + address_line
                    datalatlong = findViewById(R.id.LatLong)
                    //menampilkan data latitude longitude pada textview
                    datalatlong.text =
                        "Latitude  : " + location.latitude + " ,Longitude : " + location.longitude
                    val address_location = address[0].getAddressLine(0)
                    openLocation(address_location.toString())
                } catch (e: IOException) {

                }

            }
        }
    }


    private fun openLocation(location: String) {
        //membuka lokasi terkini dengan google maps dengan intent
        binding.textView.setOnClickListener() {
            if (!binding.textView.text.isEmpty()) {
                val uri = Uri.parse("geo:0, 0?q=$location")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        sensorManager.registerListener(this, accelerometer, SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, magnetometer, SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this, accelerometer)
        sensorManager.unregisterListener(this, magnetometer)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor === accelerometer) {
            lowPass(event.values, lastAccelerometer)
            lastAccelerometerSet = true
        } else if (event.sensor === magnetometer) {
            lowPass(event.values, lastMagnetometer)
            lastMagnetometerSet = true
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            val r = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, null, lastAccelerometer, lastMagnetometer)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                val degree = (toDegrees(orientation[0].toDouble()) + 360).toFloat() % 360

                val rotateAnimation = RotateAnimation(
                    currentDegree,
                    -degree,
                    RELATIVE_TO_SELF, 0.5f,
                    RELATIVE_TO_SELF, 0.5f)
                rotateAnimation.duration = 1000
                rotateAnimation.fillAfter = true

                image.startAnimation(rotateAnimation)
                currentDegree = -degree
            }
        }
    }

    fun lowPass(input: FloatArray, output: FloatArray) {
        val alpha = 0.05f

        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
    }
}

