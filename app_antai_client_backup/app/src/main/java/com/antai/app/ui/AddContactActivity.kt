package com.antai.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.antai.app.AppContainer
import com.antai.app.comms.AppRepository
import com.antai.app.databinding.ActivityAddContactBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Add contact with relationship tag + trusted-circle linking (MD 3.3). */
class AddContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddContactBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSave.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            val label = binding.etLabel.text.toString().trim()
            if (phone.isEmpty() || label.isEmpty()) {
                Toast.makeText(this, "Phone and label required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val tag = when (binding.spinnerTag.selectedItemPosition) {
                0 -> "mom"; 1 -> "dad"; 2 -> "son"; 3 -> "daughter"; 4 -> "grandma"
                5 -> "grandpa"; 6 -> "aunt"; 7 -> "uncle"; 8 -> "brother"; 9 -> "sister"
                else -> null
            }
            val trusted = binding.cbTrusted.isChecked
            lifecycleScope.launch(Dispatchers.IO) {
                val ok = AppRepository.addContact(phone, label, tag, trusted)
                if (ok && trusted) AppRepository.linkTrusted(phone, label, tag)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddContactActivity,
                        if (ok) "Contact saved on device + server" else "Peer must be on antAI to link",
                        Toast.LENGTH_SHORT).show()
                    if (ok) finish()
                }
            }
        }
    }
}