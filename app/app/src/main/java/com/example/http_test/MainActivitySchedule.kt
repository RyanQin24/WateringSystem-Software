package com.example.http_test

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import java.text.SimpleDateFormat
import java.util.*

class MainActivitySchedule : AppCompatActivity() {

    private lateinit var tvTime2: TextView
    private lateinit var countdownTextView1: TextView
    private lateinit var countdownTextView2: TextView
    private var timer1: CountDownTimer? = null
    private var timer2: CountDownTimer? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L // 1 second

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_schedule)

        tvTime2 = findViewById(R.id.tvTime2)
        countdownTextView1 = findViewById(R.id.countdownTextView1)
        countdownTextView2 = findViewById(R.id.countdownTextView2)

        sharedPreferences = getSharedPreferences("SchedulePrefs", MODE_PRIVATE)

        val editTextTime = findViewById<EditText>(R.id.editTextTime)
        val editTextMoisture = findViewById<EditText>(R.id.editTextMoisture)
        val editTextModule = findViewById<EditText>(R.id.editTextModule)
        val buttonSubmit = findViewById<Button>(R.id.buttonSubmit)
        val buttonReturnHome = findViewById<Button>(R.id.buttonuserPage)
        val clearButtonModule1 = findViewById<Button>(R.id.clearButton1)
        val clearButtonModule2 = findViewById<Button>(R.id.clearButton2)

        buttonSubmit.setOnClickListener {
            val time = editTextTime.text.toString()
            val moisture = editTextMoisture.text.toString()
            val module = editTextModule.text.toString()

            if (time.isEmpty() || moisture.isEmpty() || module.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                sendPostRequest(time, moisture, module)
            }
        }

        buttonReturnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        clearButtonModule1.setOnClickListener {
            timer1?.cancel()
            countdownTextView1.text = "Module 1 Countdown: 00:00:00"
            sharedPreferences.edit {
                remove("time_module_1")
                apply()
            }
        }

        clearButtonModule2.setOnClickListener {
            timer2?.cancel()
            countdownTextView2.text = "Module 2 Countdown: 00:00:00"
            sharedPreferences.edit {
                remove("time_module_2")
                apply()
            }
        }

        startSystemTimeUpdater()
        loadScheduleState()
    }

    private fun startSystemTimeUpdater() {
        handler.post(object : Runnable {
            override fun run() {
                getTime()
                handler.postDelayed(this, updateInterval)
            }
        })
    }

    private fun getTime() {
        val url = "http://192.168.68.2:5000/get_sensor_readings"
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val currentTime = response.getString("time")
                    Log.d("MainActivity", "Fetched time: $currentTime")
                    tvTime2.text = "Time: $currentTime"
                } catch (e: Exception) {
                    Toast.makeText(this, "Error parsing time: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("MainActivity", "getTime parse error: ${e.localizedMessage}")
                }
            },
            { error ->
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "getTime error: ${error.localizedMessage}")
            }
        )
        Volley.newRequestQueue(this).add(jsonObjectRequest)
    }

    private fun sendPostRequest(time: String, moisture: String, module: String) {
        val url = "http://192.168.68.2:5000/change_schedule"

        // Split time into hour and minutes
        val timeParts = time.split(":")
        if (timeParts.size != 2) {
            Toast.makeText(this, "Invalid time format. Use HH:MM.", Toast.LENGTH_SHORT).show()
            return
        }

        val hour = timeParts[0]
        val minutes = timeParts[1]

        val fullUrl = "$url?module=$module&hour=$hour&minutes=$minutes&setvalue=$moisture"

        val stringRequest = StringRequest(
            Request.Method.POST, fullUrl,
            { response ->
                // Save schedule state and start the countdown timer only if the request is successful
                saveScheduleState(time, module)
                startCountdownTimer(time, module)
            },
            { error ->
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_LONG).show()
            })

        val requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(stringRequest)
    }

    private fun startCountdownTimer(time: String, module: String) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = Calendar.getInstance().time
        val targetTime = sdf.parse(time)
        val currentDate = Calendar.getInstance()
        val targetDate = Calendar.getInstance().apply {
            this.time = targetTime
            set(Calendar.YEAR, currentDate.get(Calendar.YEAR))
            set(Calendar.MONTH, currentDate.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, currentDate.get(Calendar.DAY_OF_MONTH))
        }

        if (targetTime != null) {
            var timeDifference = targetDate.timeInMillis - currentDate.timeInMillis
            if (timeDifference < 0) {
                targetDate.add(Calendar.DAY_OF_MONTH, 1)
                timeDifference = targetDate.timeInMillis - currentDate.timeInMillis
            }

            if (module == "1") {
                timer1?.cancel()
                timer1 = object : CountDownTimer(timeDifference, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val hours = millisUntilFinished / (1000 * 60 * 60) % 24
                        val minutes = millisUntilFinished / (1000 * 60) % 60
                        val seconds = millisUntilFinished / 1000 % 60

                        countdownTextView1.text = String.format("Module 1 Countdown: %02d:%02d:%02d", hours, minutes, seconds)
                    }

                    override fun onFinish() {
                        countdownTextView1.text = "Module 1 Countdown: 00:00:00"
                    }
                }.start()
            } else if (module == "2") {
                timer2?.cancel()
                timer2 = object : CountDownTimer(timeDifference, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val hours = millisUntilFinished / (1000 * 60 * 60) % 24
                        val minutes = millisUntilFinished / (1000 * 60) % 60
                        val seconds = millisUntilFinished / 1000 % 60

                        countdownTextView2.text = String.format("Module 2 Countdown: %02d:%02d:%02d", hours, minutes, seconds)
                    }

                    override fun onFinish() {
                        countdownTextView2.text = "Module 2 Countdown: 00:00:00"
                    }
                }.start()
            }
        }
    }

    private fun saveScheduleState(time: String, module: String) {
        sharedPreferences.edit {
            putString("time_module_$module", time)
            apply()
        }
    }

    private fun loadScheduleState() {
        val timeModule1 = sharedPreferences.getString("time_module_1", null)
        val timeModule2 = sharedPreferences.getString("time_module_2", null)

        if (timeModule1 != null) {
            startCountdownTimer(timeModule1, "1")
        }

        if (timeModule2 != null) {
            startCountdownTimer(timeModule2, "2")
        }
    }
}