package com.shubham.spendtracker

import android.app.Application
import android.content.Context
import com.shubham.spendtracker.data.AppDatabase
import com.shubham.spendtracker.data.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SpendApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository by lazy { TransactionRepository(AppDatabase.create(this).transactions()) }
}

val Context.spendApp: SpendApp get() = applicationContext as SpendApp
