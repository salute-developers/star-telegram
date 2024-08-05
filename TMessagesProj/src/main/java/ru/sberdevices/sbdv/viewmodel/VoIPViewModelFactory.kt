package ru.sberdevices.sbdv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.sberdevices.sbdv.SbdvServiceLocator.getSpotterStateRepository
import ru.sberdevices.sbdv.config.Config

class VoIPViewModelFactory(
    private val voIPModel: VoIPModel,
    private val config: Config

) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        VoIPViewModel(voIPModel, getSpotterStateRepository(), config) as T
}
