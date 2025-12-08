package com.webview.browser

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LogViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "唤醒锁日志"

        val tvLogs = findViewById<TextView>(R.id.tvLogs)
        
        // Load logs
        tvLogs.text = LogManager.getLogs(this)

        // Clear logs button
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_clear_logs -> {
                    showClearLogsDialog(tvLogs)
                    true
                }
                else -> false
            }
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

    private fun showClearLogsDialog(tvLogs: TextView) {
        MaterialAlertDialogBuilder(this)
            .setTitle("清空日志")
            .setMessage("确定要清空所有唤醒锁日志吗？")
            .setPositiveButton("清空") { _, _ ->
                LogManager.clearLogs(this)
                tvLogs.text = "No logs found."
            }
            .setNegativeButton("取消", null)
            .show()
    }
}