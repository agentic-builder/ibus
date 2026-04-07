package com.aightlabs.bustrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private val database = FirebaseDatabase.getInstance().reference

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            Toast.makeText(this, "위치 권한이 허용되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다. 설정에서 허용해주세요.", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "알림 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)

        if (prefs.isRegistered()) {
            showRegisteredScreen()
            return
        }

        setContentView(R.layout.activity_main)
        requestPermissions()
        setupRegistrationForm()
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupRegistrationForm() {
        val etKindergartenName = findViewById<EditText>(R.id.etKindergartenName)
        val etVehicleName = findViewById<EditText>(R.id.etVehicleName)
        val etRoute = findViewById<EditText>(R.id.etRoute)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnRegister.setOnClickListener {
            val kindergartenName = etKindergartenName.text.toString().trim()
            val vehicleName = etVehicleName.text.toString().trim()
            val route = etRoute.text.toString().trim()

            if (kindergartenName.isEmpty()) {
                etKindergartenName.error = "어린이집 이름을 입력해주세요"
                return@setOnClickListener
            }
            if (vehicleName.isEmpty()) {
                etVehicleName.error = "차량 이름을 입력해주세요"
                return@setOnClickListener
            }
            if (route.isEmpty()) {
                etRoute.error = "운행 구간을 입력해주세요"
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = "등록 중..."
            registerVehicle(kindergartenName, vehicleName, route)
        }
    }

    private fun registerVehicle(kindergartenName: String, vehicleName: String, route: String) {
        // Search for existing kindergarten by name
        database.child("kindergartens")
            .orderByChild("name")
            .equalTo(kindergartenName)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Use existing kindergarten
                        val existingKgId = snapshot.children.first().key!!
                        createVehicle(existingKgId, kindergartenName, vehicleName, route)
                    } else {
                        // Create new kindergarten
                        val kindergartenId = "kg_${UUID.randomUUID()}"
                        val kindergartenData = mapOf(
                            "name" to kindergartenName,
                            "createdAt" to ServerValue.TIMESTAMP,
                            "shareLink" to "https://bustrack.web.app/track/$kindergartenId"
                        )
                        database.child("kindergartens").child(kindergartenId)
                            .setValue(kindergartenData)
                            .addOnSuccessListener {
                                createVehicle(kindergartenId, kindergartenName, vehicleName, route)
                            }
                            .addOnFailureListener { e ->
                                onRegistrationFailed(e.message ?: "어린이집 등록 실패")
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    onRegistrationFailed(error.message)
                }
            })
    }

    private fun createVehicle(
        kindergartenId: String,
        kindergartenName: String,
        vehicleName: String,
        route: String
    ) {
        val vehicleId = "vehicle_${UUID.randomUUID()}"
        val vehicleData = mapOf(
            "kindergartenId" to kindergartenId,
            "name" to vehicleName,
            "route" to route,
            "status" to "inactive",
            "location" to mapOf(
                "lat" to 0.0,
                "lng" to 0.0,
                "speed" to 0.0,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        )

        database.child("vehicles").child(vehicleId)
            .setValue(vehicleData)
            .addOnSuccessListener {
                prefs.saveRegistration(
                    vehicleId = vehicleId,
                    kindergartenId = kindergartenId,
                    vehicleName = vehicleName,
                    kindergartenName = kindergartenName,
                    route = route
                )
                onRegistrationSuccess()
            }
            .addOnFailureListener { e ->
                onRegistrationFailed(e.message ?: "차량 등록 실패")
            }
    }

    private fun onRegistrationSuccess() {
        Toast.makeText(this, "등록이 완료되었습니다! 홈 화면에 위젯을 추가해주세요.", Toast.LENGTH_LONG).show()
        showRegisteredScreen()
    }

    private fun onRegistrationFailed(message: String) {
        Toast.makeText(this, "등록 실패: $message", Toast.LENGTH_LONG).show()
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        btnRegister.isEnabled = true
        btnRegister.text = "등록 완료"
    }

    private fun showRegisteredScreen() {
        setContentView(R.layout.activity_main)

        val formContainer = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(
            R.id.formContainer
        )
        formContainer.visibility = android.view.View.GONE

        val registeredContainer = findViewById<android.widget.LinearLayout>(R.id.registeredContainer)
        registeredContainer.visibility = android.view.View.VISIBLE

        val tvRegisteredInfo = findViewById<TextView>(R.id.tvRegisteredInfo)
        val kindergartenName = prefs.getKindergartenName()
        val vehicleName = prefs.getVehicleName()
        val route = prefs.getRoute()
        tvRegisteredInfo.text = "${kindergartenName} ${vehicleName}\n${route}"

        val tvShareLink = findViewById<TextView>(R.id.tvShareLink)
        val kindergartenId = prefs.getKindergartenId()
        tvShareLink.text = "학부모 공유 링크:\nhttps://bustrack.web.app/track/${kindergartenId}"

        val tvWidgetGuide = findViewById<TextView>(R.id.tvWidgetGuide)
        tvWidgetGuide.text = "홈 화면을 길게 누르고\n'위젯' → '버스 위치추적'을 추가해주세요"
    }
}
