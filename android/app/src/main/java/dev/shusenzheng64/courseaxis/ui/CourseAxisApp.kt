package dev.shusenzheng64.courseaxis.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.shusenzheng64.courseaxis.core.SettingsJellyVisualPolicy
import dev.shusenzheng64.courseaxis.core.WeekGlassVisualPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.shusenzheng64.courseaxis.AppDestination
import dev.shusenzheng64.courseaxis.AppNavigationRequest
import dev.shusenzheng64.courseaxis.CourseAxisApplication
import dev.shusenzheng64.courseaxis.model.ScheduleEvent
import java.time.LocalDate
import kotlinx.coroutines.launch

enum class AppTab(val title:String,val icon:ImageVector){WEEK("周视图",Icons.Default.CalendarViewWeek),IMPORT("教务导入",Icons.Default.CloudDownload),SETTINGS("设置",Icons.Default.Settings)}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CourseAxisApp(app:CourseAxisApplication,navigationRequest:AppNavigationRequest=AppNavigationRequest(AppDestination.WEEK)){
    val document by app.repository.document.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var tab by rememberSaveable{mutableStateOf(AppTab.WEEK)}
    LaunchedEffect(navigationRequest.sequence){
        tab=when(navigationRequest.destination){
            AppDestination.WEEK->AppTab.WEEK
            AppDestination.SETTINGS->AppTab.SETTINGS
        }
    }
    var editor by remember{mutableStateOf<ScheduleEvent?>(null)}
    var adding by remember{mutableStateOf(false)}
    var defaultDate by remember{mutableStateOf(LocalDate.now())}
    val snackbar=remember{SnackbarHostState()}
    val weekTab=tab==AppTab.WEEK
    val settingsTab=tab==AppTab.SETTINGS
    val settingsStyle=tab!=AppTab.WEEK
    Scaffold(
        topBar={
            if(settingsTab)TopAppBar(
                title={Text("课轴 · ${tab.title}")},
                colors=TopAppBarDefaults.topAppBarColors(
                    containerColor=Color(SettingsJellyVisualPolicy.topBarSurfaceArgb),
                    titleContentColor=Color(SettingsJellyVisualPolicy.primaryTextArgb),
                ),
            )
        },
        bottomBar={
            NavigationBar(
                containerColor=Color(
                    if(settingsStyle)SettingsJellyVisualPolicy.navigationSurfaceArgb
                    else WeekGlassVisualPolicy.navigationSurfaceArgb,
                ),
            ){
                AppTab.entries.forEach{item->
                    NavigationBarItem(
                        selected=tab==item,
                        onClick={tab=item},
                        icon={Icon(item.icon,null)},
                        label={Text(item.title)},
                        colors=NavigationBarItemDefaults.colors(
                            selectedIconColor=Color(if(settingsStyle)SettingsJellyVisualPolicy.accentArgb else WeekGlassVisualPolicy.accentArgb),
                            selectedTextColor=Color(if(settingsStyle)SettingsJellyVisualPolicy.primaryTextArgb else WeekGlassVisualPolicy.primaryTextArgb),
                            indicatorColor=Color.White.copy(alpha=.10f),
                            unselectedIconColor=Color(if(settingsStyle)SettingsJellyVisualPolicy.subtleTextArgb else WeekGlassVisualPolicy.subtleTextArgb),
                            unselectedTextColor=Color(if(settingsStyle)SettingsJellyVisualPolicy.subtleTextArgb else WeekGlassVisualPolicy.subtleTextArgb),
                        ),
                    )
                }
            }
        },
        snackbarHost={SnackbarHost(snackbar)}
        ,containerColor=Color.Transparent
    ){padding->when(tab){
        AppTab.WEEK->WeekScreen(
            document=document,
            contentPadding=padding,
            onEdit={event->editor=event},
            onAdd={date->defaultDate=date;adding=true},
            onEditTimeRule={tab=AppTab.SETTINGS},
            onActivateTerm={termId->
                if(document.settings.activeTermId!=termId){
                    scope.launch{app.repository.setActiveTerm(termId)}
                }
            },
        )
        AppTab.IMPORT->ImportScreen(app.repository,padding,snackbar)
        AppTab.SETTINGS->SettingsScreen(app,padding,snackbar)
    }}
    if(adding||editor!=null)EventEditor(editor,defaultDate,document,app.repository,snackbar){adding=false;editor=null}
}
