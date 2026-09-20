package com.joaohouto.clusterlauncher.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.joaohouto.clusterlauncher.data.model.DockSlot
import com.joaohouto.clusterlauncher.data.model.DockSlotType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dock_preferences")

class DockPreferences(private val context: Context) {

    fun getDockSlotsFlow(): Flow<List<DockSlot>> {
        return context.dataStore.data.map { preferences ->
            DockSlotType.entries.map { slotType ->
                val key = stringPreferencesKey("slot_package_${slotType.id}")
                val savedPackage = preferences[key]
                val resolvedPackage = if (!savedPackage.isNullOrEmpty() && AutomotivePackageResolver.isPackageInstalled(context.packageManager, savedPackage)) {
                    savedPackage
                } else {
                    AutomotivePackageResolver.resolveDefaultPackage(context, slotType)
                }
                DockSlot(type = slotType, targetPackage = resolvedPackage)
            }
        }
    }

    suspend fun setSlotPackage(slotType: DockSlotType, packageName: String) {
        val key = stringPreferencesKey("slot_package_${slotType.id}")
        context.dataStore.edit { preferences ->
            preferences[key] = packageName
        }
    }

    private val CAR_BRAND_KEY = stringPreferencesKey("car_brand_id")

    fun getCarBrandFlow(): Flow<com.joaohouto.clusterlauncher.data.model.CarBrand> {
        return context.dataStore.data.map { preferences ->
            val savedId = preferences[CAR_BRAND_KEY]
            com.joaohouto.clusterlauncher.data.model.CarBrand.getBrandById(savedId)
        }
    }

    suspend fun setCarBrand(brandId: String) {
        context.dataStore.edit { preferences ->
            preferences[CAR_BRAND_KEY] = brandId
        }
    }
}
