package com.shubham.spendtracker

import android.app.Application
import android.content.Context
import com.shubham.spendtracker.data.AppDatabase
import com.shubham.spendtracker.data.DailyLimit
import com.shubham.spendtracker.data.TransactionRepository
import com.shubham.spendtracker.insights.MonthlySummaryWorker
import com.shubham.spendtracker.widget.SpendWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SpendApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository by lazy { TransactionRepository(AppDatabase.create(this).transactions()) }

    override fun onCreate() {
        super.onCreate()
        MonthlySummaryWorker.schedule(this)
        // Any payment, category change or limit change redraws the home screen widget.
        appScope.launch {
            combine(repository.transactions, DailyLimit.limit(this@SpendApp)) { _, _ -> }
                .collect { SpendWidget.refresh(this@SpendApp) }
        }
    }
}

val Context.spendApp: SpendApp get() = applicationContext as SpendApp
