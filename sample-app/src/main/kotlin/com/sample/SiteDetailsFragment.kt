package com.sample.voiceagent

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.voiceagent.kit.VoiceAgent
import com.voiceagent.kit.schema.FieldType
import com.voiceagent.kit.schema.FormSchema
import com.voiceagent.kit.schema.SchemaMode
import com.voiceagent.kit.schema.VoiceField

// ══════════════════════════════════════════════════════════════════════════════
// BEFORE (OLD DDFin pattern — ~500 lines of boilerplate per fragment):
//
//   private var liveKitManager: LiveKitManager? = null
//   private var hybridModeManager: HybridModeManager? = null
//   private var connectionStateListener: ((Boolean) -> Unit)? = null
//   private var pendingFormData: LiveKitManager.FormData? = null
//   private var lastSiteName: String = ""
//   private var lastOwnership: String = ""
//   private var lastIrrigationSource: String = ""
//   ... 10-15 more lastXxx variables ...
//   private val formDataListener = { formData -> ... 40 lines ... }
//   fun setupLiveKitIntegration() { ... }
//   fun teardownLiveKitIntegration() { ... }
//   fun checkAndSendInitialData() { ... }
//   fun sendInitialDataToLiveKit() { ... 40 lines ... }
//   fun applyPendingFormData() { ... }
//   fun applyLiveKitSiteName(value: String?) { ... }
//   fun applyLiveKitOwnership(value: String?) { ... }
//   fun applyLiveKitIrrigationSource(value: String?) { ... }
//   ... 8-15 more apply functions ...
//   override fun onResume() { setupLiveKitIntegration(); applyPendingFormData() }
//   override fun onPause() { removeFormDataListener(); cancelConnectionCheck() }
//
// AFTER (new VoiceAgentKit SDK pattern — 15 lines of integration):
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Sample fragment demonstrating the exact DDFin "Add New Site" fragment migration.
 *
 * The ONLY voice-related code is the [FormSchema] definition and the
 * [VoiceAgent.attach()] call. Everything else is normal fragment logic.
 */
class SiteDetailsFragment : Fragment() {

    private val TAG = "SiteDetailsFragment"

    // ──────────────────────────────────────────────────────────────
    // Simulated view bindings (in real DDFin, replace with actual ViewBinding)
    // ──────────────────────────────────────────────────────────────
    private lateinit var tieSiteName: android.widget.EditText
    private lateinit var rgOwnOrLeased: android.widget.RadioGroup
    private lateinit var spinnerIrrigationSource: android.widget.Spinner
    private lateinit var cbIsOrganic: android.widget.CheckBox
    private lateinit var tieAreaInAcres: android.widget.EditText
    private lateinit var btnSubmit: android.widget.Button

    // ──────────────────────────────────────────────────────────────
    // SDK: Schema definition — replaces all hardcoded JSON key references
    // ──────────────────────────────────────────────────────────────
    private val voiceSchema by lazy {
        FormSchema(
            screenId = "add_new_site",
            mode     = SchemaMode.ADD,
            fields   = listOf(
                VoiceField(
                    id        = "site_name",
                    viewRef   = tieSiteName,
                    fieldType = FieldType.TEXT,
                    isRequired = true
                ),
                VoiceField(
                    id        = "ownership",
                    viewRef   = rgOwnOrLeased,
                    fieldType = FieldType.RADIO,
                    options   = listOf("own", "leased"),
                    isRequired = true
                ),
                VoiceField(
                    id        = "irr_source",
                    viewRef   = spinnerIrrigationSource,
                    fieldType = FieldType.SPINNER,
                    options   = listOf("Borewell/Tube well", "Canal/River", "Well", "Tank/Pond"),
                    // Backend also sends "irrigation_source" for this field
                    jsonKeyAliases = listOf("irrigation_source", "draft_irrigation_source")
                ),
                VoiceField(
                    id        = "is_organic",
                    viewRef   = cbIsOrganic,
                    fieldType = FieldType.CHECKBOX
                ),
                VoiceField(
                    id        = "area_in_acres",
                    viewRef   = tieAreaInAcres,
                    fieldType = FieldType.NUMBER,
                    isRequired = true
                )
            )
        )
    }

    // ──────────────────────────────────────────────────────────────
    // Fragment lifecycle
    // ──────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // In real DDFin, use ViewBinding:
        //   binding = FragmentSiteDetailsBinding.inflate(inflater, container, false)
        //   return binding.root
        //
        // For this sample, we inflate a placeholder layout:
        val root = View(requireContext())
        initializeViews(root)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinnerAdapter()
        setupSubmitButton()

        // ──────────────────────────────────────────────────────────
        // SDK: This single call replaces ALL ~500 lines of boilerplate.
        // The attachment is lifecycle-aware — no onResume/onPause/onDestroyView needed.
        // ──────────────────────────────────────────────────────────
        VoiceAgent.attach(
            fragment        = this,
            schema          = voiceSchema,
            onFieldFilled   = { fieldId, value ->
                Log.d(TAG, "Voice filled '$fieldId' → '$value'")
                validateFormAndUpdateButton()
            },
            onFormCompleted = {
                Log.i(TAG, "All required fields filled by voice")
                handleAddSiteSubmit()
            },
            onNavigate      = { screenName ->
                when (screenName) {
                    "digitization_method" -> handleAddSiteSubmit()
                    "dashboard_home"      -> findNavController().navigateUp()
                    else -> Log.w(TAG, "Unknown navigation target: $screenName")
                }
            },
            onError = { error ->
                Log.e(TAG, "VoiceAgent error: $error")
            }
        )
        // ══════ That's it. No more setupLiveKitIntegration(), no more boilerplate. ══════
    }

    // ──────────────────────────────────────────────────────────────
    // Original DDFin non-voice logic — completely UNCHANGED
    // ──────────────────────────────────────────────────────────────

    private fun setupSpinnerAdapter() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("Borewell/Tube well", "Canal/River", "Well", "Tank/Pond")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerIrrigationSource.adapter = adapter
    }

    private fun setupSubmitButton() {
        btnSubmit.isEnabled = false
        btnSubmit.setOnClickListener {
            handleAddSiteSubmit()
        }
    }

    private fun validateFormAndUpdateButton() {
        val siteNameFilled = tieSiteName.text?.isNotBlank() == true
        val ownershipSelected = rgOwnOrLeased.checkedRadioButtonId != -1
        val areaFilled = tieAreaInAcres.text?.isNotBlank() == true
        btnSubmit.isEnabled = siteNameFilled && ownershipSelected && areaFilled
    }

    private fun handleAddSiteSubmit() {
        val siteName = tieSiteName.text?.toString()?.trim() ?: ""
        if (siteName.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a site name", Toast.LENGTH_SHORT).show()
            return
        }

        // In real DDFin: call your ViewModel / API
        Log.i(TAG, "Submitting site: $siteName")
        Toast.makeText(requireContext(), "Site '$siteName' added!", Toast.LENGTH_SHORT).show()

        // Navigate back
        findNavController().navigateUp()
    }

    private fun initializeViews(root: View) {
        // In real DDFin, these come from ViewBinding:
        //   tieSiteName           = binding.tieSiteName
        //   rgOwnOrLeased         = binding.rgOwnOrLeased
        //   spinnerIrrigationSource = binding.spinnerIrrigationSource
        //   cbIsOrganic           = binding.cbIsOrganic
        //   tieAreaInAcres        = binding.tieAreaInAcres
        //   btnSubmit             = binding.btnAddSite
        tieSiteName             = android.widget.EditText(requireContext())
        rgOwnOrLeased           = android.widget.RadioGroup(requireContext())
        spinnerIrrigationSource = android.widget.Spinner(requireContext())
        cbIsOrganic             = android.widget.CheckBox(requireContext())
        tieAreaInAcres          = android.widget.EditText(requireContext())
        btnSubmit               = android.widget.Button(requireContext())
    }
}
