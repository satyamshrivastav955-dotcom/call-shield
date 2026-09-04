package com.antai.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.antai.app.AppContainer
import com.antai.app.comms.AppRepository
import com.antai.app.data.local.ReportEntity
import com.antai.app.databinding.ActivityHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** History screen: verdicts, post-call/post-chat reports, call log (MD 4.4/4.9). */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var verdictAdapter: VerdictHistoryAdapter
    private lateinit var reportAdapter: ReportHistoryAdapter
    private lateinit var callAdapter: CallHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verdictAdapter = VerdictHistoryAdapter()
        reportAdapter = ReportHistoryAdapter()
        callAdapter = CallHistoryAdapter()
        reportAdapter.onTap = { openReport(it) }

        binding.rvVerdicts.layoutManager = LinearLayoutManager(this)
        binding.rvVerdicts.adapter = verdictAdapter
        binding.rvReports.layoutManager = LinearLayoutManager(this)
        binding.rvReports.adapter = reportAdapter
        binding.rvCalls.layoutManager = LinearLayoutManager(this)
        binding.rvCalls.adapter = callAdapter

        binding.chipVerdicts.setOnClickListener { showTab(0) }
        binding.chipReports.setOnClickListener { showTab(1) }
        binding.chipCalls.setOnClickListener { showTab(2) }
        showTab(0)

        lifecycleScope.launch {
            AppContainer.db.verdicts().observeAll().collect { verdictAdapter.submit(it) }
        }
        lifecycleScope.launch {
            AppContainer.db.reports().observeAll().collect { reportAdapter.submit(it) }
        }
        lifecycleScope.launch {
            AppContainer.db.calls().observeAll().collect { callAdapter.submit(it) }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { AppRepository.syncHistory() }
        }
    }

    private fun showTab(tab: Int) {
        binding.rvVerdicts.visibility = if (tab == 0) android.view.View.VISIBLE else android.view.View.GONE
        binding.rvReports.visibility = if (tab == 1) android.view.View.VISIBLE else android.view.View.GONE
        binding.rvCalls.visibility = if (tab == 2) android.view.View.VISIBLE else android.view.View.GONE
        binding.chipVerdicts.isChecked = tab == 0
        binding.chipReports.isChecked = tab == 1
        binding.chipCalls.isChecked = tab == 2
    }

    private fun openReport(report: ReportEntity) {
        val i = Intent(this, ReportActivity::class.java)
        i.putExtra("title", report.title)
        i.putExtra("body", report.body)
        i.putExtra("kind", report.kind)
        i.putExtra("scam_type", report.scamType ?: "unknown")
        startActivity(i)
    }
}