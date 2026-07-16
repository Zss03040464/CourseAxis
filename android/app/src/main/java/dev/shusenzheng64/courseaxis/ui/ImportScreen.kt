package dev.shusenzheng64.courseaxis.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewFeature
import dev.shusenzheng64.courseaxis.BuildConfig
import dev.shusenzheng64.courseaxis.core.SettingsJellyVisualPolicy
import dev.shusenzheng64.courseaxis.data.ScheduleRepository
import dev.shusenzheng64.courseaxis.data.SecureCredentialStore
import dev.shusenzheng64.courseaxis.importer.AcademicCredentials
import dev.shusenzheng64.courseaxis.importer.AcademicDefaultProfileState
import dev.shusenzheng64.courseaxis.importer.AcademicImportMessageChannel
import dev.shusenzheng64.courseaxis.importer.AcademicLoginAction
import dev.shusenzheng64.courseaxis.importer.AcademicLoginPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicLoginScript
import dev.shusenzheng64.courseaxis.importer.AcademicPageSnapshot
import dev.shusenzheng64.courseaxis.importer.AcademicNavigationDestination
import dev.shusenzheng64.courseaxis.importer.AcademicProviderCatalog
import dev.shusenzheng64.courseaxis.importer.AcademicProviderDisplayPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicProviderSelectionPolicy
import dev.shusenzheng64.courseaxis.importer.AcademicWebDataCleaner
import dev.shusenzheng64.courseaxis.importer.AcademicWebSession
import dev.shusenzheng64.courseaxis.importer.AcademicWebSessionBinding
import dev.shusenzheng64.courseaxis.importer.AcademicWebViewPolicy
import dev.shusenzheng64.courseaxis.importer.ImportWorkspacePolicy
import dev.shusenzheng64.courseaxis.importer.AcademicImportAdapterRegistry
import dev.shusenzheng64.courseaxis.importer.AcademicImportBundle
import dev.shusenzheng64.courseaxis.importer.AcademicImportOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint(
    "SetJavaScriptEnabled",
)
@Composable
fun ImportScreen(
    repository: ScheduleRepository,
    contentPadding: PaddingValues,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val document by repository.document.collectAsStateWithLifecycle()
    val catalogResult = remember { runCatching { AcademicProviderCatalog.load(context.assets) } }
    val providers = catalogResult.getOrDefault(emptyList())
    val providerLabels = remember(providers) { AcademicProviderDisplayPolicy.labels(providers) }
    val selection = remember(
        providers,
        document.settings.selectedAcademicProviderId,
        document.settings.activeTermId,
        document.terms,
    ) {
        AcademicProviderSelectionPolicy.resolve(
            providers = providers,
            requestedProviderId = document.settings.selectedAcademicProviderId,
            terms = document.terms,
            activeTermId = document.settings.activeTermId,
        )
    }
    val provider = selection.provider
    val activeTerm = selection.term
    val providerLogin = provider?.let { it.allowedOrigin + it.loginPath }
    val providerHome = provider?.let { it.allowedOrigin + it.homePath }
    val catalogFailure = catalogResult.exceptionOrNull()?.message
        ?: if (provider == null) "没有可用的学校教务配置" else null
    val supportsMultipleProfiles = remember {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }

    val credentialStoreResult = remember(provider?.id) {
        provider?.let { selected ->
            runCatching { SecureCredentialStore(context, providerId = selected.id) }
        }
    }
    val credentialStore = credentialStoreResult?.getOrNull()
    val credentialStoreFailure = credentialStoreResult?.exceptionOrNull()
    val defaultProfileState = remember { AcademicDefaultProfileState(context) }
    var credentialsLoaded by remember { mutableStateOf(false) }
    var storedCredentials by remember { mutableStateOf<AcademicCredentials?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var credentialLoadFailure by remember(provider?.id) { mutableStateOf<String?>(null) }
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf(ImportConnectionStatus.LOADING) }
    var automaticAttempts by remember { mutableIntStateOf(0) }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var sessionBinding by remember { mutableStateOf<AcademicWebSessionBinding?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingImportOptions by remember { mutableStateOf<AcademicImportOptions?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sessionFailure by remember(provider?.id) { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<AcademicImportBundle?>(null) }
    val scriptResult = remember(provider?.id) {
        provider?.let { selected ->
            runCatching {
                val adapter = AcademicImportAdapterRegistry.adapter(selected)
                context.assets.open(adapter.scriptResource).bufferedReader().use { it.readText() }
            }
        }
    }

    val importResultHandler by rememberUpdatedState<(String) -> Unit> { raw ->
        busy = false
        runCatching {
            val options = requireNotNull(pendingImportOptions) { "请先在设置中选择学期" }
            AcademicImportAdapterRegistry.adapter(options.provider).parse(raw, options)
        }.onSuccess { preview = it }
            .onFailure { error = it.message ?: "解析失败" }
        pendingImportOptions = null
    }
    val messageChannel = remember(provider?.id) {
        provider?.let { selected ->
            AcademicImportMessageChannel(selected) { importResultHandler(it) }
        }
    }

    LaunchedEffect(
        provider?.id,
        document.settings.selectedAcademicProviderId,
        activeTerm?.id,
        document.settings.activeTermId,
    ) {
        val selected = provider ?: return@LaunchedEffect
        if (document.settings.selectedAcademicProviderId != selected.id) {
            repository.setSettings(
                repository.document.value.settings.copy(selectedAcademicProviderId = selected.id),
            )
        }
        if (activeTerm != null && document.settings.activeTermId != activeTerm.id) {
            repository.setActiveTerm(activeTerm.id)
        }
    }

    LaunchedEffect(provider?.id) {
        messageChannel?.remove()
        pendingImportOptions = null
        busy = false
        preview = null
        webView = null
        sessionBinding = null
        automaticAttempts = 0
        credentialsLoaded = false
        storedCredentials = null
        username = ""
        password = ""
        status = if (provider == null) ImportConnectionStatus.FAILED else ImportConnectionStatus.LOADING

        val store = credentialStore
        if (store != null) {
            val saved = withContext(Dispatchers.IO) { runCatching { store.load() } }
                .getOrElse {
                    credentialLoadFailure = "无法读取当前学校的本机账号"
                    credentialsLoaded = true
                    settingsExpanded = true
                    return@LaunchedEffect
                }
            storedCredentials = saved
            username = saved?.username.orEmpty()
            credentialsLoaded = true
            settingsExpanded = saved == null
            if (saved == null) status = ImportConnectionStatus.NEED_CREDENTIALS
        } else {
            credentialsLoaded = true
            settingsExpanded = true
        }
    }

    val pageHandler by rememberUpdatedState<(WebView, AcademicPageSnapshot) -> Unit>(
        newValue = pageHandler@ { view, page ->
        if (!credentialsLoaded) return@pageHandler
        when (
            AcademicLoginPolicy.decide(
                page = page,
                hasCredentials = storedCredentials != null,
                automaticAttempts = automaticAttempts,
            )
        ) {
            AcademicLoginAction.CONNECTED -> {
                status = ImportConnectionStatus.CONNECTED
                automaticAttempts = 0
                settingsExpanded = false
            }
            AcademicLoginAction.NEED_CREDENTIALS -> {
                status = ImportConnectionStatus.NEED_CREDENTIALS
                settingsExpanded = false
            }
            AcademicLoginAction.FILL_AND_SUBMIT -> {
                val credentials = storedCredentials ?: return@pageHandler
                status = ImportConnectionStatus.SIGNING_IN
                automaticAttempts += 1
                view.evaluateJavascript(AcademicLoginScript.fill(credentials, submit = true), null)
            }
            AcademicLoginAction.FILL_AND_WAIT_CAPTCHA -> {
                val credentials = storedCredentials ?: return@pageHandler
                status = ImportConnectionStatus.CAPTCHA
                settingsExpanded = false
                view.evaluateJavascript(AcademicLoginScript.fill(credentials, submit = false), null)
            }
            AcademicLoginAction.STOPPED -> status = ImportConnectionStatus.STOPPED
        }
    },
    )

    LaunchedEffect(credentialsLoaded, storedCredentials, webView) {
        if (credentialsLoaded) {
            val view = webView ?: return@LaunchedEffect
            view.evaluateJavascript(AcademicLoginScript.probe()) { raw ->
                pageHandler(view, AcademicPageSnapshot.fromEvaluationResult(raw))
            }
        }
    }

    val selectProvider: (String) -> Unit = { label ->
        val providerId = providerLabels.entries.firstOrNull { it.value == label }?.key
        val next = providers.firstOrNull { it.id == providerId }
        if (next != null && next.id != provider?.id) {
            scope.launch {
                repository.setSettings(
                    repository.document.value.settings.copy(
                        selectedAcademicProviderId = next.id,
                    ),
                )
                document.terms
                    .filter { it.providerId == next.id }
                    .maxByOrNull { it.updatedAt }
                    ?.let { repository.setActiveTerm(it.id) }
            }
        }
    }

    ImportGlassBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ImportConnectionPanel(
                status = if (
                    sessionFailure == null &&
                    credentialStoreFailure == null &&
                    credentialLoadFailure == null
                ) {
                    status
                } else {
                    ImportConnectionStatus.FAILED
                },
                termSummary = when {
                    provider == null -> catalogFailure.orEmpty()
                    activeTerm != null -> "${provider.schoolName} · ${activeTerm.name} · " +
                        activeTerm.campus.ifBlank { "未设置校区" }
                    else -> "${provider.schoolName} · 请先在设置中创建该学校学期"
                },
            )
            if (provider != null) {
                JellyDropdownField(
                    value = providerLabels.getValue(provider.id),
                    options = providerLabels.values.toList(),
                    onValueChange = selectProvider,
                    label = "学校教务",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                )
                if (!supportsMultipleProfiles && providers.size > 1) {
                    Text(
                        text = "当前系统 WebView 无法同时保留多个学校登录；切换学校时会先清除上一学校会话。",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = Color(SettingsJellyVisualPolicy.secondaryTextArgb),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (scriptResult?.isFailure == true) {
                    Text(
                        text = "当前学校的教务适配器资源不可用，读取已禁用。",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (credentialStoreFailure != null || credentialLoadFailure != null) {
                    Text(
                        text = "当前学校的本机账号存储不可用，账号保存已禁用。",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            ImportSettingsDisclosure(
                expanded = settingsExpanded,
                accountSummary = buildString {
                    provider?.schoolName?.let { append(it).append(" · ") }
                    append(
                        storedCredentials?.let {
                            "已保存 ${SecureCredentialStore.maskUsername(it.username)}"
                        } ?: "尚未保存账号",
                    )
                },
                onToggle = { settingsExpanded = !settingsExpanded },
            ) {
                JellyOutlinedTextField(
                    username,
                    { username = it },
                    "教务用户名",
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                JellyOutlinedTextField(
                    password,
                    { password = it },
                    if (storedCredentials == null) "教务密码" else "新密码（留空则不更改）",
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                JellyPrimaryButton(
                    icon = Icons.Default.Save,
                    text = ImportWorkspacePolicy.credentialSaveLabel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = credentialStore != null && credentialLoadFailure == null,
                    onClick = {
                        val store = credentialStore ?: return@JellyPrimaryButton
                        val candidate = runCatching {
                            ImportWorkspacePolicy.credentialsForSave(username, password, storedCredentials)
                        }.getOrElse {
                            error = it.message ?: "账号保存失败"
                            return@JellyPrimaryButton
                        }
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { store.save(candidate) }
                            }.onSuccess {
                                credentialLoadFailure = null
                                storedCredentials = candidate
                                username = candidate.username
                                password = ""
                                automaticAttempts = 0
                                settingsExpanded = false
                                snackbar.showSnackbar("账号已保存")
                                AcademicWebDataCleaner.clear(webView, sessionBinding) {
                                    providerLogin?.let { webView?.loadUrl(it) }
                                }
                            }.onFailure { error = it.message ?: "账号保存失败" }
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JellySecondaryButton(
                        icon = Icons.Default.RestartAlt,
                        text = "重新登录",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            messageChannel?.remove()
                            pendingImportOptions = null
                            busy = false
                            preview = null
                            automaticAttempts = 0
                            status = ImportConnectionStatus.LOADING
                            AcademicWebDataCleaner.clear(webView, sessionBinding) {
                                providerHome?.let { webView?.loadUrl(it) }
                            }
                        },
                    )
                    JellySecondaryButton(
                        icon = Icons.Default.DeleteOutline,
                        text = "清除账号",
                        modifier = Modifier.weight(.72f),
                        enabled = credentialStore != null,
                        onClick = {
                            val store = credentialStore ?: return@JellySecondaryButton
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { store.clear() }
                                }.onSuccess {
                                    messageChannel?.remove()
                                    pendingImportOptions = null
                                    busy = false
                                    preview = null
                                    credentialLoadFailure = null
                                    storedCredentials = null
                                    username = ""
                                    password = ""
                                    automaticAttempts = 0
                                    status = ImportConnectionStatus.NEED_CREDENTIALS
                                    settingsExpanded = true
                                    AcademicWebDataCleaner.clear(webView, sessionBinding) {
                                        providerLogin?.let { webView?.loadUrl(it) }
                                        scope.launch {
                                            snackbar.showSnackbar("已清除账号、登录会话与网页缓存")
                                        }
                                    }
                                }.onFailure { error = it.message ?: "账号清除失败" }
                            }
                        },
                    )
                }
            }
            if (provider != null && providerHome != null) {
                key(provider.id) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                val binding = runCatching {
                                    AcademicWebSession.bind(
                                        webView = this,
                                        provider = provider,
                                        lastDefaultProfileProviderId = defaultProfileState.lastProviderId(),
                                    )
                                }.getOrElse {
                                    setBackgroundColor(AndroidColor.TRANSPARENT)
                                    webView = this
                                    sessionFailure = "无法为${provider.schoolName}建立独立教务会话"
                                    error = sessionFailure
                                    return@apply
                                }
                                sessionBinding = binding
                                AcademicWebViewPolicy.configure(this, provider, binding.cookieManager)
                                settings.userAgentString =
                                    settings.userAgentString + " CourseAxis/${BuildConfig.VERSION_NAME}"
                                setBackgroundColor(AndroidColor.TRANSPARENT)
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?,
                                    ) {
                                        if (
                                            messageChannel?.active == true &&
                                            !AcademicWebViewPolicy.canEvaluateImport(url, provider)
                                        ) {
                                            messageChannel.remove()
                                            busy = false
                                            pendingImportOptions = null
                                            error = "页面已离开允许的教务范围，读取已取消"
                                        }
                                        status = ImportConnectionStatus.LOADING
                                    }

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        binding.cookieManager.flush()
                                        if (!AcademicWebViewPolicy.canEvaluateImport(url, provider)) return
                                        view.evaluateJavascript(AcademicLoginScript.probe()) { raw ->
                                            val page = AcademicPageSnapshot.fromEvaluationResult(raw)
                                            pageHandler(view, page)
                                            val options = pendingImportOptions
                                            if (messageChannel?.active != true || options == null) {
                                                return@evaluateJavascript
                                            }
                                            if (page.isLoginPage) {
                                                messageChannel.remove()
                                                busy = false
                                                pendingImportOptions = null
                                                error = "登录会话尚未就绪，请完成登录或验证码后重试"
                                                return@evaluateJavascript
                                            }
                                            val importScript = scriptResult?.getOrElse {
                                                messageChannel.remove()
                                                busy = false
                                                pendingImportOptions = null
                                                error = it.message ?: "无法读取当前教务适配器"
                                                return@evaluateJavascript
                                            } ?: return@evaluateJavascript
                                            val invocation = runCatching {
                                                AcademicImportAdapterRegistry.adapter(options.provider)
                                                    .makeInvocation(
                                                        provider = options.provider,
                                                        academicYear = options.academicYear,
                                                        semesterCode = options.semesterCode,
                                                        script = importScript,
                                                    )
                                            }.getOrElse {
                                                messageChannel.remove()
                                                busy = false
                                                pendingImportOptions = null
                                                error = it.message ?: "不支持当前教务系统"
                                                return@evaluateJavascript
                                            }
                                            view.evaluateJavascript(invocation, null)
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        webError: WebResourceError,
                                    ) {
                                        if (request.isForMainFrame) {
                                            messageChannel?.remove()
                                            busy = false
                                            pendingImportOptions = null
                                            status = ImportConnectionStatus.FAILED
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean = when (
                                        AcademicWebViewPolicy.destination(
                                            request.url.toString(),
                                            provider,
                                        )
                                    ) {
                                        AcademicNavigationDestination.EMBEDDED -> false
                                        AcademicNavigationDestination.EXTERNAL_BROWSER -> {
                                            if (request.isForMainFrame) {
                                                messageChannel?.remove()
                                                busy = false
                                                pendingImportOptions = null
                                                runCatching {
                                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                                                }
                                            }
                                            true
                                        }
                                        AcademicNavigationDestination.BLOCKED -> true
                                    }
                                }
                                webView = this
                                val startSession: () -> Unit = {
                                    runCatching {
                                        defaultProfileState.markBound(binding)
                                        loadUrl(providerHome)
                                    }.onFailure {
                                        sessionFailure = "无法启动${provider.schoolName}教务会话"
                                        error = sessionFailure
                                    }
                                    Unit
                                }
                                if (binding.requiresDefaultProfileClear) {
                                    AcademicWebDataCleaner.clear(this, binding, startSession)
                                } else {
                                    startSession()
                                }
                            }
                        },
                        update = { webView = it },
                        onRelease = {
                            messageChannel?.remove()
                            if (webView === it) webView = null
                            if (sessionBinding?.providerId == provider.id) sessionBinding = null
                            it.stopLoading()
                            it.destroy()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                .8.dp,
                                Color(SettingsJellyVisualPolicy.highlightArgb),
                                RoundedCornerShape(18.dp),
                            ),
                    )
                }
            } else {
                Text(
                    text = catalogFailure.orEmpty(),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            JellyPrimaryButton(
                icon = Icons.Default.CloudDownload,
                text = when {
                    busy -> "读取中…"
                    provider == null -> "没有可用的学校教务配置"
                    sessionFailure != null -> "教务会话不可用"
                    scriptResult?.isFailure == true -> "教务适配器不可用"
                    activeTerm == null -> "请先在设置中选择学期"
                    else -> ImportWorkspacePolicy.primaryActionLabel
                },
                enabled = !busy &&
                    provider != null &&
                    activeTerm != null &&
                    scriptResult?.isSuccess == true &&
                    sessionFailure == null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                onClick = {
                    val selectedProvider = provider ?: return@JellyPrimaryButton
                    val term = activeTerm ?: return@JellyPrimaryButton
                    val channel = messageChannel ?: return@JellyPrimaryButton
                    webView?.let { view ->
                        if (!AcademicWebViewPolicy.canEvaluateImport(view.url, selectedProvider)) {
                            error = "请先在${selectedProvider.schoolName}教务页面内完成登录"
                            return@JellyPrimaryButton
                        }
                        pendingImportOptions = AcademicImportOptions(
                            provider = selectedProvider,
                            academicYear = term.academicYear,
                            semesterCode = term.semesterCode,
                            termName = term.name,
                            firstWeekMonday = term.firstWeekMondayDate(),
                            totalWeeks = term.totalWeeks,
                            campus = term.campus,
                        )
                        if (!channel.install(view)) {
                            pendingImportOptions = null
                            error = "当前系统 WebView 不支持安全导入通道，请更新 Android System WebView"
                            return@JellyPrimaryButton
                        }
                        busy = true
                        view.reload()
                    }
                },
            )
        }
    }

    preview?.let { bundle ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("导入预览") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("课程 ${bundle.courseCount} 门 · 考试 ${bundle.examCount} 项 · 待确认 ${bundle.unresolvedCount} 项")
                    Text("作息：${bundle.timeRule?.sections?.size ?: 0} 节。只有解析出真实时间的事件才会进入时间轴。")
                    bundle.diagnostics.takeLast(8).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.applySchoolImport(bundle)
                            snackbar.showSnackbar("导入完成：${bundle.events.size} 项")
                            preview = null
                        }
                    },
                    enabled = bundle.canCommit,
                ) { Text("写入") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("取消") } },
        )
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("操作失败") },
            text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("确定") } },
        )
    }
}
