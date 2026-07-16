package dev.shusenzheng64.courseaxis

import android.app.Application
import android.content.Context
import dev.shusenzheng64.courseaxis.data.ScheduleRepository
import dev.shusenzheng64.courseaxis.data.SecureSyncSettings
import dev.shusenzheng64.courseaxis.data.SyncClient
import dev.shusenzheng64.courseaxis.widget.WidgetUpdateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CourseAxisApplication : Application() {
    lateinit var repository: ScheduleRepository
        private set
    lateinit var syncSettings: SecureSyncSettings
        private set
    lateinit var syncClient: SyncClient
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repository = ScheduleRepository(this)
        syncSettings = SecureSyncSettings(this)
        syncClient = SyncClient(repository, syncSettings)
        WidgetUpdateCoordinator.ensurePeriodic(this)
        scope.launch {
            WidgetUpdateCoordinator.onDocumentChanged(
                this@CourseAxisApplication,
                repository.document.value,
            )
        }
    }

    companion object {
        fun from(context: Context) = context.applicationContext as CourseAxisApplication
    }
}
