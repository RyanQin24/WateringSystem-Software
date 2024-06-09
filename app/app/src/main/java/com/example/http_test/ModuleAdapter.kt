package com.example.http_test

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley

data class Module(val id: Int, val name: String, val moisture: Int, val ping: Boolean, val pump: Boolean, val waterLevel: Int)

class ModuleAdapter(private val context: Context, private val modules: List<Module>) : RecyclerView.Adapter<ModuleAdapter.ModuleViewHolder>() {

    class ModuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvModuleName: TextView = itemView.findViewById(R.id.tvModuleName)
        val tvMoisture: TextView = itemView.findViewById(R.id.tvMoisture)
//        val tvHumidity: TextView = itemView.findViewById(R.id.tvHumidity)
        val tvPing: TextView = itemView.findViewById(R.id.tvPing)
        val tvPump: TextView = itemView.findViewById(R.id.tvPump)
        val tvWaterLevel: TextView = itemView.findViewById(R.id.tvWaterLevel)
        val btnTestPump: Button = itemView.findViewById(R.id.buttonTestPump)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.module_item, parent, false)
        return ModuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        val module = modules[position]
        holder.tvModuleName.text = module.name
        holder.tvMoisture.text = "Moisture: ${module.moisture}"
        holder.tvPing.text = "Ping: ${if (module.ping) "Yes" else "No"}"
        holder.tvPump.text = "Pump: ${if (module.pump) "Yes" else "No"}"
        holder.tvWaterLevel.text = "Water Level: ${if (module.waterLevel == 1) "OK" else "Refill"}"

        holder.btnTestPump.setOnClickListener {
            val url = "http://192.168.68.2:5000/test?module=${module.id}"
            val stringRequest = StringRequest(Request.Method.GET, url,
                { response ->
                    Toast.makeText(context, "Pump test initiated for ${module.name}", Toast.LENGTH_SHORT).show()
                },
                { error ->
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
            Volley.newRequestQueue(context).add(stringRequest)
        }
    }

    override fun getItemCount(): Int = modules.size
}
