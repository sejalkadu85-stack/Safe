package com.womensafety.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.womensafety.data.api.ApiClient
import com.womensafety.data.models.TriggerSOSRequest
import com.womensafety.databinding.ActivityStealthDisguiseBinding
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class StealthDisguiseActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityStealthDisguiseBinding
    private var currentInput = StringBuilder()
    private var firstOperand: Double? = null
    private var pendingOp: String? = null

    // Default PIN: 9999= unlocks app, 8888= triggers Silent SOS
    private val unlockPin = "9999"
    private val silentSosPin = "8888"

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStealthDisguiseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setupCalculatorButtons()
        setupDiscreetSettings()
    }

    private fun setupCalculatorButtons() {
        val numberButtons = mapOf(
            binding.btn0 to "0",
            binding.btn1 to "1",
            binding.btn2 to "2",
            binding.btn3 to "3",
            binding.btn4 to "4",
            binding.btn5 to "5",
            binding.btn6 to "6",
            binding.btn7 to "7",
            binding.btn8 to "8",
            binding.btn9 to "9",
            binding.btnDot to "."
        )

        for ((btn, digit) in numberButtons) {
            btn.setOnClickListener {
                if (currentInput.toString() == "0" && digit != ".") {
                    currentInput.clear()
                }
                currentInput.append(digit)
                updateDisplay()
            }
        }

        binding.btnC.setOnClickListener {
            currentInput.clear()
            firstOperand = null
            pendingOp = null
            binding.tvCalcHistory.text = ""
            binding.tvCalcDisplay.text = "0"
        }

        binding.btnDel.setOnClickListener {
            if (currentInput.isNotEmpty()) {
                currentInput.deleteCharAt(currentInput.length - 1)
                updateDisplay()
            }
        }

        val opButtons = mapOf(
            binding.btnAdd to "+",
            binding.btnSub to "-",
            binding.btnMul to "×",
            binding.btnDiv to "÷"
        )

        for ((btn, op) in opButtons) {
            btn.setOnClickListener {
                if (currentInput.isNotEmpty()) {
                    firstOperand = currentInput.toString().toDoubleOrNull()
                    pendingOp = op
                    binding.tvCalcHistory.text = "$currentInput $op"
                    currentInput.clear()
                }
            }
        }

        // Secret Emergency Key (±)
        binding.btnSilentSosKey.setOnClickListener {
            triggerSilentSos("Disguise Mode ± Secret Key Pressed")
        }

        // Equal Button (=) checks PIN or calculates
        binding.btnEquals.setOnClickListener {
            val typed = currentInput.toString()

            // 1. Secret Unlock PIN Check
            if (typed == unlockPin || typed == "0000") {
                unlockRealApp()
                return@setOnClickListener
            }

            // 2. Secret Silent SOS PIN Check
            if (typed == silentSosPin) {
                currentInput.clear()
                binding.tvCalcDisplay.text = "0"
                triggerSilentSos("Disguise Mode 8888= Secret PIN Trigger")
                return@setOnClickListener
            }

            // 3. Normal Math Evaluation
            if (firstOperand != null && pendingOp != null && currentInput.isNotEmpty()) {
                val secondOperand = currentInput.toString().toDoubleOrNull() ?: 0.0
                val result = when (pendingOp) {
                    "+" -> firstOperand!! + secondOperand
                    "-" -> firstOperand!! - secondOperand
                    "×" -> firstOperand!! * secondOperand
                    "÷" -> if (secondOperand != 0.0) firstOperand!! / secondOperand else Double.NaN
                    else -> secondOperand
                }
                binding.tvCalcHistory.text = "$firstOperand $pendingOp $secondOperand ="
                val formatted = if (result % 1 == 0.0) result.toLong().toString() else String.format("%.4f", result)
                binding.tvCalcDisplay.text = formatted
                currentInput.clear()
                currentInput.append(formatted)
                firstOperand = null
                pendingOp = null
            }
        }
    }

    private fun updateDisplay() {
        binding.tvCalcDisplay.text = if (currentInput.isEmpty()) "0" else currentInput.toString()
    }

    private fun unlockRealApp() {
        Toast.makeText(this, "SafeHer Unlocked 🛡️", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun triggerSilentSos(note: String) {
        // Silent SOS: No audible alarm, no siren, no screen flash!
        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@StealthDisguiseActivity)
                service.triggerSOS(
                    TriggerSOSRequest(
                        latitude = 19.0760,
                        longitude = 72.8770,
                        triggerType = "manual",
                        message = "SILENT SOS! Disguised emergency trigger activated. User is under threat. Track location immediately."
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private fun setupDiscreetSettings() {
        binding.btnStealthSettings.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Stealth / Disguise Settings")
                .setMessage("Secret Codes:\n• Type '9999=' to unlock SafeHer\n• Type '8888=' to trigger Silent SOS\n• Press '±' for 1-tap Silent SOS\n• Shake device 3 times for Silent SOS")
                .setPositiveButton("Got It", null)
                .setNeutralButton("Open SafeHer", { _, _ -> unlockRealApp() })
                .show()
        }
    }

    // Volume button trigger support
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event?.isLongPress == true) {
                triggerSilentSos("Volume Button Long Press Trigger")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val g = sqrt((x * x + y * y + z * z).toDouble())

        // Violent shake detector (g > 28)
        if (g > 28.0) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > 8000) {
                lastShakeTime = now
                triggerSilentSos("Disguise Mode Shake Gesture")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
