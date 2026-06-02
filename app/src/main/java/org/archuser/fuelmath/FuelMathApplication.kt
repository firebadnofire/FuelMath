package org.archuser.fuelmath

import android.app.Application
import com.google.android.material.color.DynamicColors

class FuelMathApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
