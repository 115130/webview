package com.webview.browser

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class BlockedDomainsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var adapter: BlockedDomainsAdapter
    private val domains = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_domains)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        
        val rvBlockedDomains = findViewById<RecyclerView>(R.id.rvBlockedDomains)
        val fabAddDomain = findViewById<FloatingActionButton>(R.id.fabAddDomain)

        adapter = BlockedDomainsAdapter(domains) { domain ->
            removeDomain(domain)
        }
        
        rvBlockedDomains.layoutManager = LinearLayoutManager(this)
        rvBlockedDomains.adapter = adapter

        fabAddDomain.setOnClickListener {
            showAddDomainDialog()
        }

        loadDomains()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadDomains() {
        val blockedDomainsStr = sharedPreferences.getString("blocked_domains", "") ?: ""
        val list = blockedDomainsStr.split(Regex("[,，]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        domains.clear()
        domains.addAll(list)
        adapter.notifyDataSetChanged()
    }

    private fun saveDomains() {
        val blockedDomainsStr = domains.joinToString(",")
        sharedPreferences.edit().putString("blocked_domains", blockedDomainsStr).apply()
    }

    private fun addDomain(domain: String) {
        if (domain.isNotEmpty() && !domains.contains(domain)) {
            domains.add(domain)
            adapter.notifyItemInserted(domains.size - 1)
            saveDomains()
        }
    }

    private fun removeDomain(domain: String) {
        val index = domains.indexOf(domain)
        if (index != -1) {
            domains.removeAt(index)
            adapter.notifyItemRemoved(index)
            saveDomains()
        }
    }

    private fun showAddDomainDialog() {
        val input = EditText(this)
        input.hint = "输入域名或网址 (例如 example.com)"
        // Add padding
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)

        MaterialAlertDialogBuilder(this)
            .setTitle("添加防误触域名")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    // Simple normalization if user pastes full URL
                    val domain = if (text.contains("://")) {
                        try {
                            android.net.Uri.parse(text).host ?: text
                        } catch (e: Exception) {
                            text
                        }
                    } else {
                        text
                    }
                    addDomain(domain)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}