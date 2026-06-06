package com.starryxoxo.chevronbreadcrumbs

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.starryxoxo.chevronbreadcrumbs.databinding.ActivityExclusionBinding
import com.starryxoxo.chevronbreadcrumbs.databinding.ItemAppExclusionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ExclusionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExclusionBinding
    private val prefs by lazy { getSharedPreferences("ChevronPrefs", Context.MODE_PRIVATE) }
    private var excludedApps = mutableSetOf<String>()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { performExport(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { performImport(it) }
    }

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable,
        var isExcluded: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExclusionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.exclusions_title)

        excludedApps = prefs.getStringSet("excludedApps", emptySet())?.toMutableSet() ?: mutableSetOf()

        binding.btnExport.setOnClickListener {
            exportLauncher.launch("exclusions.txt")
        }
        binding.btnImport.setOnClickListener {
            importLauncher.launch("text/plain")
        }

        loadApps()
    }

    private fun performExport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val data = excludedApps.joinToString("\n")
                    outputStream.write(data.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExclusionActivity, "Exclusions exported!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExclusionActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performImport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val lines = reader.readLines().filter { it.isNotBlank() }
                    
                    withContext(Dispatchers.Main) {
                        excludedApps.addAll(lines)
                        prefs.edit().putStringSet("excludedApps", excludedApps).apply()
                        loadApps() // Refresh list
                        Toast.makeText(this@ExclusionActivity, "Imported ${lines.size} apps!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExclusionActivity, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadApps() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                
                // Get all home launcher packages
                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val launchers = pm.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }.toSet()

                // Get all apps that appear in the launcher
                val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val launchableApps = pm.queryIntentActivities(launchIntent, 0).map { it.activityInfo.packageName }.toSet()

                allApps.filter { app ->
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    // Show it if:
                    // 1. It's not a system app
                    // 2. It's a launcher (Home app)
                    // 3. it has a launcher icon (launchable)
                    // 4. It's explicitly "launcher" or "settings"
                    !isSystem || 
                    launchers.contains(app.packageName) || 
                    launchableApps.contains(app.packageName) ||
                    app.packageName.contains("launcher", ignoreCase = true) ||
                    app.packageName == "com.android.settings"
                }.map { app ->
                    AppInfo(
                        name = pm.getApplicationLabel(app).toString(),
                        packageName = app.packageName,
                        icon = pm.getApplicationIcon(app),
                        isExcluded = excludedApps.contains(app.packageName)
                    )
                }.distinctBy { it.packageName }
                .sortedBy { it.name.lowercase() }
            }
            
            binding.rvApps.layoutManager = LinearLayoutManager(this@ExclusionActivity)
            binding.rvApps.adapter = AppAdapter(apps)
            binding.progressBar.visibility = View.GONE
            binding.rvApps.visibility = View.VISIBLE
        }
    }

    inner class AppAdapter(private val apps: List<AppInfo>) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: ItemAppExclusionBinding) : RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemAppExclusionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.itemBinding.tvAppName.text = app.name
            holder.itemBinding.ivAppIcon.setImageDrawable(app.icon)
            holder.itemBinding.cbExcluded.isChecked = app.isExcluded

            holder.itemView.setOnClickListener {
                app.isExcluded = !app.isExcluded
                holder.itemBinding.cbExcluded.isChecked = app.isExcluded
                
                if (app.isExcluded) {
                    excludedApps.add(app.packageName)
                } else {
                    excludedApps.remove(app.packageName)
                }
                prefs.edit().putStringSet("excludedApps", excludedApps).apply()
            }
        }

        override fun getItemCount() = apps.size
    }
}
