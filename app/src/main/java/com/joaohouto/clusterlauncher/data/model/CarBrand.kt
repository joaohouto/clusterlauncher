package com.joaohouto.clusterlauncher.data.model

import androidx.annotation.DrawableRes
import com.joaohouto.clusterlauncher.R

data class CarBrand(
    val id: String,
    val name: String,
    @get:DrawableRes val iconRes: Int
) {
    companion object {
        val ALL_BRANDS = listOf(
            CarBrand("android", "Android", R.drawable.ic_brand_android),
            CarBrand("volkswagen", "Volkswagen", R.drawable.ic_brand_volkswagen),
            CarBrand("chevrolet", "Chevrolet", R.drawable.ic_brand_chevrolet),
            CarBrand("fiat", "Fiat", R.drawable.ic_brand_fiat),
            CarBrand("ford", "Ford", R.drawable.ic_brand_ford),
            CarBrand("toyota", "Toyota", R.drawable.ic_brand_toyota),
            CarBrand("honda", "Honda", R.drawable.ic_brand_honda),
            CarBrand("hyundai", "Hyundai", R.drawable.ic_brand_hyundai),
            CarBrand("renault", "Renault", R.drawable.ic_brand_renault),
            CarBrand("jeep", "Jeep", R.drawable.ic_brand_jeep),
            CarBrand("nissan", "Nissan", R.drawable.ic_brand_nissan),
            CarBrand("peugeot", "Peugeot", R.drawable.ic_brand_peugeot),
            CarBrand("citroen", "Citroën", R.drawable.ic_brand_citroen),
            CarBrand("bmw", "BMW", R.drawable.ic_brand_bmw),
            CarBrand("mercedes", "Mercedes-Benz", R.drawable.ic_brand_mercedes),
            CarBrand("audi", "Audi", R.drawable.ic_brand_audi),
            CarBrand("kia", "Kia", R.drawable.ic_brand_kia),
            CarBrand("mitsubishi", "Mitsubishi", R.drawable.ic_brand_mitsubishi),
            CarBrand("volvo", "Volvo", R.drawable.ic_brand_volvo),
            CarBrand("subaru", "Subaru", R.drawable.ic_brand_subaru),
            CarBrand("porsche", "Porsche", R.drawable.ic_brand_porsche),
            CarBrand("tesla", "Tesla", R.drawable.ic_brand_tesla)
        )

        fun getBrandById(id: String?): CarBrand {
            if (id == "cluster" || id == "android") return ALL_BRANDS.first()
            return ALL_BRANDS.firstOrNull { it.id == id } ?: ALL_BRANDS.first()
        }
    }
}
