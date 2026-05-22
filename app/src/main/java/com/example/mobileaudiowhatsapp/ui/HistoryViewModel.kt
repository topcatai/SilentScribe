package com.example.mobileaudiowhatsapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.mobileaudiowhatsapp.History
import com.example.mobileaudiowhatsapp.data.AppDatabase
import com.example.mobileaudiowhatsapp.data.CallLog
import com.example.mobileaudiowhatsapp.data.CallLogDao
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val dao: CallLogDao) : ViewModel() {
    var query by mutableStateOf("")
        private set

    fun onQueryChange(q: String) {
        query = q
    }

    val logs: StateFlow<List<CallLog>> = snapshotFlow { query }
        .debounce(150)
        .flatMapLatest { q ->
            if (q.isBlank()) dao.observeAll() else dao.search(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    class Factory(private val dao: CallLogDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                return HistoryViewModel(dao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

@Composable
fun historyViewModel(navController: NavHostController, backStackEntry: NavBackStackEntry): HistoryViewModel {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember(context) { AppDatabase.getInstance(context).callLogDao() }
    val entry = remember(backStackEntry) { navController.getBackStackEntry<History>() }
    return viewModel(
        viewModelStoreOwner = entry,
        factory = HistoryViewModel.Factory(dao)
    )
}
