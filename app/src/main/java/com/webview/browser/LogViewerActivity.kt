package com.webview.browser

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webview.browser.databinding.ActivityLogViewerBinding

class LogViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用 Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-Edge 适配
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 设置状态栏颜色
        window.statusBarColor = getColor(R.color.primary_dark)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.log_viewer_title)

        // Load logs
        loadLogs()

        // Clear logs button
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_clear_logs -> {
                    showClearLogsDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadLogs() {
        val logs = LogManager.getLogs(this)
        if (logs == "No logs found.") {
             binding.tvLogs.text = getString(R.string.no_logs_found)
        } else {
             binding.tvLogs.text = logs
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_log_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showClearLogsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_logs)
            .setMessage(R.string.clear_logs_confirmation)
            .setPositiveButton(R.string.clear) { _, _ ->
                LogManager.clearLogs(this)
                binding.tvLogs.text = getString(R.string.no_logs_found)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}