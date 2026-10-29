package com.Shyam.xplayer.vault

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.Shyam.xplayer.R

class VaultActivity : AppCompatActivity() {

    private val enteredPin = StringBuilder()
    private var isSettingUp = false
    private var firstEnteredPin = ""
    private lateinit var dots: List<View>
    private lateinit var tvPrompt: TextView
    private lateinit var tvSubPrompt: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_vault)

        val root = findViewById<View>(R.id.vaultAuthRoot)
        val header = findViewById<View>(R.id.vaultHeader)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density
            header.setPadding((16 * density).toInt(), statusBar.top + (8 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            root.setPadding(0, 0, 0, navBar.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBackVault).setOnClickListener { finish() }

        tvPrompt = findViewById(R.id.tvVaultPrompt)
        tvSubPrompt = findViewById(R.id.tvVaultSubPrompt)
        dots = listOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4)
        )

        VaultSecurityManager.restoreConfigIfCleared(this)
        val isSetup = VaultSecurityManager.isVaultSetup(this)

        if (!isSetup) {
            isSettingUp = true
            tvPrompt.text = "Create Vault PIN"
            tvSubPrompt.text = "Enter a 4-digit master PIN to secure your vault"
        } else {
            isSettingUp = false
            tvPrompt.text = "Enter Vault PIN"
            tvSubPrompt.text = "Enter your 4-digit PIN to unlock private files"
        }

        setupKeypad()
    }

    private fun setupKeypad() {
        val numButtons = listOf(
            R.id.btnKey0 to "0", R.id.btnKey1 to "1", R.id.btnKey2 to "2",
            R.id.btnKey3 to "3", R.id.btnKey4 to "4", R.id.btnKey5 to "5",
            R.id.btnKey6 to "6", R.id.btnKey7 to "7", R.id.btnKey8 to "8",
            R.id.btnKey9 to "9"
        )

        for ((id, digit) in numButtons) {
            findViewById<Button>(id).setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin.append(digit)
                    updateDots()
                    if (enteredPin.length == 4) {
                        handlePinComplete()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnKeyDel).setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin.deleteCharAt(enteredPin.length - 1)
                updateDots()
            }
        }

        findViewById<Button>(R.id.btnKeyForgot).setOnClickListener {
            showForgotPinDialog()
        }
    }

    private fun updateDots() {
        for (i in dots.indices) {
            if (i < enteredPin.length) {
                dots[i].backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#AECBFA"))
            } else {
                dots[i].backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#40FFFFFF"))
            }
        }
    }

    private fun handlePinComplete() {
        val pin = enteredPin.toString()
        enteredPin.clear()

        if (isSettingUp) {
            if (firstEnteredPin.isEmpty()) {
                firstEnteredPin = pin
                tvPrompt.text = "Confirm Vault PIN"
                tvSubPrompt.text = "Re-enter your 4-digit PIN to confirm"
                updateDots()
            } else {
                if (firstEnteredPin == pin) {
                    showSecurityQuestionSetupDialog(pin)
                } else {
                    Toast.makeText(this, "PINs did not match. Try again.", Toast.LENGTH_SHORT).show()
                    firstEnteredPin = ""
                    tvPrompt.text = "Create Vault PIN"
                    tvSubPrompt.text = "Enter a 4-digit master PIN to secure your vault"
                    updateDots()
                }
            }
        } else {
            updateDots()
            if (VaultSecurityManager.verifyMasterPasscode(this, pin)) {
                startActivity(Intent(this, VaultGalleryActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Incorrect PIN. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSecurityQuestionSetupDialog(pin: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }
        val etQuestion = EditText(this).apply {
            hint = "Security Question (e.g. Favorite City?)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setText("What is your childhood nickname?")
        }
        val etAnswer = EditText(this).apply {
            hint = "Answer"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#80FFFFFF"))
        }
        layout.addView(etQuestion)
        layout.addView(etAnswer)

        AlertDialog.Builder(this)
            .setTitle("Set Recovery Question")
            .setMessage("Used to reset your PIN if forgotten.")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Finish Setup") { _, _ ->
                val q = etQuestion.text.toString().trim()
                val a = etAnswer.text.toString().trim()
                if (q.isNotEmpty() && a.isNotEmpty()) {
                    VaultSecurityManager.setMasterPasscode(this, pin, VaultSecurityManager.LockType.PIN)
                    VaultSecurityManager.setSecurityQuestion(this, q, a)
                    Toast.makeText(this, "Vault successfully protected!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, VaultGalleryActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Question and Answer cannot be empty.", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showForgotPinDialog() {
        val question = VaultSecurityManager.getSecurityQuestion(this)
        if (question.isNullOrEmpty()) {
            Toast.makeText(this, "No recovery question set.", Toast.LENGTH_SHORT).show()
            return
        }

        val etAnswer = EditText(this).apply {
            hint = "Enter your answer"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setPadding(40, 40, 40, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Forgot PIN?")
            .setMessage("Question: $question")
            .setView(etAnswer)
            .setPositiveButton("Verify") { _, _ ->
                val ans = etAnswer.text.toString().trim()
                if (VaultSecurityManager.verifySecurityAnswer(this, ans)) {
                    Toast.makeText(this, "Answer verified. Please set a new PIN.", Toast.LENGTH_SHORT).show()
                    isSettingUp = true
                    firstEnteredPin = ""
                    tvPrompt.text = "Create New PIN"
                    tvSubPrompt.text = "Enter a new 4-digit PIN"
                    enteredPin.clear()
                    updateDots()
                } else {
                    Toast.makeText(this, "Incorrect answer.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
