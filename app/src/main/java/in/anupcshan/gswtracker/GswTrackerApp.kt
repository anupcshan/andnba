package `in`.anupcshan.gswtracker

import android.app.Application
import `in`.anupcshan.gswtracker.data.api.DataUsageTracker

class GswTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DataUsageTracker.init(this)
    }
}
