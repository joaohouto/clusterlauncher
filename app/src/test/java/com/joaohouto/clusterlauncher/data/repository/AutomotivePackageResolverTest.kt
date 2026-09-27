package com.joaohouto.clusterlauncher.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomotivePackageResolverTest {

    @Test
    fun knownBluetoothPackages_containsEssentialPlatforms() {
        val packages = AutomotivePackageResolver.KNOWN_BLUETOOTH_PACKAGES

        // Verifies top automotive head unit platforms are present
        assertTrue("Must support FYT/Syu platform", packages.contains("com.syu.bt"))
        assertTrue("Must support Topway TS10/TS18/TS7 platform", packages.contains("com.ts.bt"))
        assertTrue("Must support Microntek/PX5/PX6 platform", packages.contains("com.microntek.bluetooth"))
        assertTrue("Must support XYAuto/AC8227L platform", packages.contains("com.xyauto.bluetooth"))
        assertTrue("Must support Autochips platform", packages.contains("com.autochips.bluetooth"))
        assertTrue("Must support Allwinner platform", packages.contains("com.allwinner.bluetooth"))
        assertTrue("Must support Nowada platform", packages.contains("com.nwd.bluetooth"))
        assertTrue("Must support Android Automotive dialer", packages.contains("com.android.car.dialer"))
    }

    @Test
    fun knownBluetoothPackages_noDuplicates() {
        val packages = AutomotivePackageResolver.KNOWN_BLUETOOTH_PACKAGES
        val unique = packages.toSet()
        assertTrue("Package list should not have duplicates", packages.size == unique.size)
    }
}
