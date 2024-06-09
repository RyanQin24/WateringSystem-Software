package com.example.http_test

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request as VolleyRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import org.json.JSONException

class MainActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var rvModules: RecyclerView
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L // 5 seconds
    private lateinit var currentPhotoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions() // Check and request permissions

        tvTime = findViewById(R.id.tvTime)
        rvModules = findViewById(R.id.rvModules)
        rvModules.layoutManager = LinearLayoutManager(this)

        val buttonSchedulePage = findViewById<Button>(R.id.buttonSchedulePage)
        buttonSchedulePage.setOnClickListener {
            val intent = Intent(this, MainActivitySchedule::class.java)
            startActivity(intent)
        }

        val buttonAiHelper = findViewById<Button>(R.id.buttonAiHelper)
        buttonAiHelper.setOnClickListener {
            Log.d("MainActivity", "AI Helper button clicked")
            dispatchTakePictureIntent()
        }

        startFetchingTime()
        startFetchingModules()
    }

    private fun startFetchingTime() {
        handler.post(object : Runnable {
            override fun run() {
                getTime()
                handler.postDelayed(this, updateInterval)
            }
        })
    }

    private fun startFetchingModules() {
        handler.post(object : Runnable {
            override fun run() {
                getModules()
                handler.postDelayed(this, updateInterval)
            }
        })
    }

    private fun getTime() {
        val url = "http://192.168.68.2:5000/get_sensor_readings"
        val jsonObjectRequest = JsonObjectRequest(VolleyRequest.Method.GET, url, null,
            { response ->
                try {
                    val currentTime = response.getString("time")
                    Log.d("MainActivity", "Fetched time: $currentTime")
                    tvTime.text = "Time: $currentTime"
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

    private fun getModules() {
        val url = "http://192.168.68.2:5000/get_sensor_readings"
        val jsonObjectRequest = JsonObjectRequest(VolleyRequest.Method.GET, url, null,
            { response ->
                try {
                    Log.d("MainActivity", "Response: $response") // Log the response
                    val modules = mutableListOf<Module>()
                    val moduleIds = response.getString("modules").split(",")
                    val moistureLevels = response.getString("moisture").split(",")
                    val pingStatuses = response.getString("ping").split(",")
                    val pumpStatuses = response.getString("pump").split(",")
                    val waterLevels = response.getString("waterlevel").split(",")

                    for (i in moduleIds.indices) {
                        if (moduleIds[i].isNotEmpty()) {
                            val moduleName = "Module ${moduleIds[i]}"
                            val id = moduleIds[i].toInt()
                            val moisture = moistureLevels[i].toInt()
                            val ping = pingStatuses[i] == "Yes"
                            val pump = pumpStatuses[i] == "Running"
                            val waterLevel = if (waterLevels[i] == "OK") 1 else 0
                            modules.add(Module(id, moduleName, moisture, ping, pump, waterLevel))
                        }
                    }

                    rvModules.adapter = ModuleAdapter(this, modules)
                    Log.d("MainActivity", "Modules: $modules") // Log the parsed modules
                } catch (e: Exception) {
                    Toast.makeText(this, "Error parsing modules: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("MainActivity", "getModules parse error: ${e.localizedMessage}")
                }
            },
            { error ->
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "getModules error: ${error.localizedMessage}")
            }
        )
        Volley.newRequestQueue(this).add(jsonObjectRequest)
    }

    private fun dispatchTakePictureIntent() {
        Log.d("MainActivity", "dispatchTakePictureIntent called")
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Log.e("MainActivity", "Error creating image file: ${ex.message}")
                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.example.http_test.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Log.d("MainActivity", "Image capture succeeded")
            val file = File(currentPhotoPath)
            sendImageToPlantNet(file) // Send image to PlantNet API
        } else {
            Log.d("MainActivity", "Image capture failed or cancelled")
        }
    }

    private fun sendImageToPlantNet(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()

                // Create multipart form data
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("images", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                    .addFormDataPart("organs", "leaf")
                    .build()

                val request = OkHttpRequest.Builder()
                    .url("https://my-api.plantnet.org/v2/identify/all?api-key=YOUR_PLANTNET_API_KEY")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d("MainActivity", "PlantNet API Response: $responseBody") // Log the response

                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val results = jsonResponse.getJSONArray("results")
                    if (results.length() > 0) {
                        val plantName = results.getJSONObject(0).getJSONObject("species").getString("scientificNameWithoutAuthor")
                        withContext(Dispatchers.Main) {
                            sendPlantNameToChatGPT(plantName)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showPlantName("Unknown plant")
                        }
                    }
                } else {
                    val errorMessage = responseBody ?: "Unknown error"
                    Log.e("MainActivity", "Failed to get response from PlantNet: ${response.code} - $errorMessage")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Failed to get response from PlantNet: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Log.e("MainActivity", "Error during upload to PlantNet: ${e.message}")
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun sendPlantNameToChatGPT(plantName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val mediaType = "application/json".toMediaTypeOrNull()

            val requestBodyContent = """
            {
                "model": "gpt-4-turbo",
                "messages": [
                    {"role": "system", "content": "You are a helpful assistant."},
                    {"role": "user", "content": "Provide a suggestion for the $plantName plant in the following format:\n\n1. Plant Description (20 words)\n2. Suggested Moisture Sensor Value (70-150)\n3. Best Watering Schedule (Days: /Hours: /Minutes)"}
                ]
            }
        """.trimIndent()

            val requestBody = requestBodyContent.toRequestBody(mediaType)

            val request = OkHttpRequest.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer YOUR_GPT_API_KEY")
                .post(requestBody)
                .build()

            try {
                // Log the request body for debugging
                Log.d("MainActivity", "Request Body: $requestBodyContent")

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d("MainActivity", "ChatGPT Response: $responseBody")

                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val suggestion = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    runOnUiThread {
                        showFormattedSuggestion(suggestion)
                    }
                } else {
                    val errorMessage = responseBody ?: "Unknown error"
                    Log.e("MainActivity", "Failed to get response from ChatGPT: ${response.code} - $errorMessage")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to get response from ChatGPT: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Log.e("MainActivity", "Error during upload to ChatGPT: ${e.message}")
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun showFormattedSuggestion(suggestion: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_suggestion, null)
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvDialogMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)

        val formattedSuggestion = suggestion.replace("\n", "\n\n") // Add extra newline for better readability
        tvDialogMessage.text = formattedSuggestion

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }




    private fun showSuggestion(suggestion: String?) {
        AlertDialog.Builder(this)
            .setTitle("AI Helper Suggestion")
            .setMessage(suggestion)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }


    private fun showPlantName(plantName: String) {
        AlertDialog.Builder(this)
            .setTitle("Plant Name Recognized")
            .setMessage("The plant recognized is: $plantName")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 0)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            // All permissions granted
        } else {
            Toast.makeText(this, "Permissions required for camera and storage", Toast.LENGTH_LONG).show()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
    }
}
