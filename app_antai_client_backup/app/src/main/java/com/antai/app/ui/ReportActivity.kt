package com.antai.app.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.antai.app.databinding.ActivityReportBinding

/** Post-call/post-chat report viewer (MD 4.4). */
class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = intent.getStringExtra("title") ?: "Report"
        binding.tvMeta.text = "Type: ${intent.getStringExtra("kind") ?: ""} · Scam type: ${intent.getStringExtra("scam_type") ?: "unknown"}"
        binding.tvBody.text = intent.getStringExtra("body") ?: ""
    }
}