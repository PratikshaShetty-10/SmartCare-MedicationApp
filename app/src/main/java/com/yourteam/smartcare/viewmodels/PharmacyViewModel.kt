// PharmacyViewModel.kt
package com.yourteam.smartcare.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.smartcare.repositories.Pharmacy
import com.yourteam.smartcare.repositories.PharmacyRepository
import kotlinx.coroutines.launch

class PharmacyViewModel(private val repository: PharmacyRepository) : ViewModel() {
    var pharmacyList = mutableStateOf<List<Pharmacy>>(emptyList())

    fun fetchNearbyPharmacies(lat: Double, lng: Double) {
        viewModelScope.launch {
            pharmacyList.value = repository.findNearbyPharmacies(lat, lng)
        }
    }
}