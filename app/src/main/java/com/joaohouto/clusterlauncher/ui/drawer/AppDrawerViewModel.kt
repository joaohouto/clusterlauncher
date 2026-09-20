package com.joaohouto.clusterlauncher.ui.drawer

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joaohouto.clusterlauncher.data.model.AppItem
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.data.model.DockSlotType
import com.joaohouto.clusterlauncher.data.repository.AppDrawerRepository
import com.joaohouto.clusterlauncher.data.repository.DockPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppDrawerViewModel(application: Application) : AndroidViewModel(application) {

    private val appDrawerRepository = AppDrawerRepository(application)
    private val dockPreferences = DockPreferences(application)

    private val _apps = MutableStateFlow<List<AppItem>>(emptyList())
    val apps: StateFlow<List<AppItem>> = _apps.asStateFlow()

    val dockSlots: StateFlow<List<DockSlot>> = dockPreferences.getDockSlotsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DockSlotType.entries.map { DockSlot(it) }
        )

    val carBrand: StateFlow<com.joaohouto.clusterlauncher.data.model.CarBrand> = dockPreferences.getCarBrandFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.joaohouto.clusterlauncher.data.model.CarBrand.ALL_BRANDS.first()
        )

    fun selectCarBrand(brand: com.joaohouto.clusterlauncher.data.model.CarBrand) {
        viewModelScope.launch {
            dockPreferences.setCarBrand(brand.id)
        }
    }

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _apps.value = appDrawerRepository.getInstalledApps()
        }
    }

    fun assignAppToSlot(slotType: DockSlotType, packageName: String) {
        viewModelScope.launch {
            dockPreferences.setSlotPackage(slotType, packageName)
        }
    }

    fun launchApp(context: Context, packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }
}
