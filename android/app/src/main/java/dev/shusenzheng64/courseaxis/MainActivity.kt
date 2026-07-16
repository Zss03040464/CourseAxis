package dev.shusenzheng64.courseaxis
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.shusenzheng64.courseaxis.ui.CourseAxisApp
import dev.shusenzheng64.courseaxis.ui.CourseAxisTheme
import dev.shusenzheng64.courseaxis.ui.WindowGlassController

class MainActivity : ComponentActivity() {
    private val navigationRequest = mutableStateOf(AppNavigationRequest(AppDestination.WEEK))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationRequest.value = AppNavigationRequest(
            AppRoute.parse(intent.getStringExtra(EXTRA_INITIAL_TAB)),
        )
        enableEdgeToEdge()
        WindowGlassController.apply(window)
        setContent {
            CourseAxisTheme {
                CourseAxisApp(CourseAxisApplication.from(this), navigationRequest.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigationRequest.value = AppNavigationRequest(
            destination = AppRoute.parse(intent.getStringExtra(EXTRA_INITIAL_TAB)),
            sequence = navigationRequest.value.sequence + 1,
        )
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "initial_tab"
        const val ROUTE_WEEK = "week"
    }
}
