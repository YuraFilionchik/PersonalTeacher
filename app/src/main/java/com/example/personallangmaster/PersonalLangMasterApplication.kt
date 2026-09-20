package com.example.personallangmaster

import android.app.Application
import com.example.personallangmaster.di.AppContainer

/** Точка сборки зависимостей приложения. */
class PersonalLangMasterApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.warmUp()
    }
}
