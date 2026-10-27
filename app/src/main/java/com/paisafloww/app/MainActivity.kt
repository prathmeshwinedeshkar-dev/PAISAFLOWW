package com.paisafloww.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paisafloww.app.data.AppDatabase
import com.paisafloww.app.data.FinanceRepository
import com.paisafloww.app.ui.AppRoot
import com.paisafloww.app.ui.MainViewModel

class PaisaApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: FinanceRepository by lazy { FinanceRepository(database) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            AppRoot(vm)
        }
    }
}
