package dev.shusenzheng64.courseaxis.widget
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import dev.shusenzheng64.courseaxis.core.ScheduleEngine
import dev.shusenzheng64.courseaxis.data.ScheduleStorage
import dev.shusenzheng64.courseaxis.model.ScheduleDocument
import java.time.*
import java.util.concurrent.TimeUnit
object WidgetUpdateCoordinator{
    private const val PERIODIC="courseaxis-widget-periodic";private const val BOUNDARY="courseaxis-widget-boundary"
    suspend fun onDocumentChanged(context:Context,document:ScheduleDocument){CourseAxisWidget().updateAll(context);scheduleBoundary(context,document);ensurePeriodic(context)}
    fun ensurePeriodic(context:Context){val request=PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15,TimeUnit.MINUTES).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,request)}
    fun scheduleBoundary(context:Context,document:ScheduleDocument=ScheduleStorage.read(context)){val now=ZonedDateTime.now();val b=ScheduleEngine.nextBoundary(document,LocalDate.now(),LocalTime.now());if(b==null){WorkManager.getInstance(context).cancelUniqueWork(BOUNDARY);return};val target=b.first.atTime(b.second).atZone(now.zone).plusSeconds(1);val delay=Duration.between(now,target).toMillis().coerceAtLeast(1000);val request=OneTimeWorkRequestBuilder<WidgetBoundaryWorker>().setInitialDelay(delay,TimeUnit.MILLISECONDS).build();WorkManager.getInstance(context).enqueueUniqueWork(BOUNDARY,ExistingWorkPolicy.REPLACE,request)}
}
class WidgetRefreshWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){override suspend fun doWork():Result=runCatching{CourseAxisWidget().updateAll(applicationContext);WidgetUpdateCoordinator.scheduleBoundary(applicationContext);Result.success()}.getOrElse{Result.retry()}}
class WidgetBoundaryWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){override suspend fun doWork():Result=runCatching{CourseAxisWidget().updateAll(applicationContext);WidgetUpdateCoordinator.scheduleBoundary(applicationContext);Result.success()}.getOrElse{Result.retry()}}
