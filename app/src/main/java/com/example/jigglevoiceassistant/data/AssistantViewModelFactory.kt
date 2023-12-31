package com.example.jigglevoiceassistant.data

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.jigglevoiceassistant.assistant.AssistantViewModel
import java.lang.IllegalArgumentException

class AssistantViewModelFactory (private val dataSource: AssistantDao, private val application: Application) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if(modelClass.isAssignableFrom(AssistantViewModel::class.java)){
            return AssistantViewModel(dataSource, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

}