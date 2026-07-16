package dev.shusenzheng64.courseaxis.importer

object ImportWorkspacePolicy {
    const val primaryActionLabel = "读取课程、考试与真实作息"
    const val credentialSaveLabel = "保存账号"
    const val showsLegacyQuickActions = false
    const val showsTermFields = false

    fun credentialsForSave(
        username: String,
        password: String,
        existing: AcademicCredentials?,
    ): AcademicCredentials {
        val normalizedUsername = username.trim()
        val resolvedPassword = password.ifEmpty { existing?.password.orEmpty() }
        require(normalizedUsername.isNotEmpty() && resolvedPassword.isNotEmpty()) {
            "请填写教务用户名和密码"
        }
        return AcademicCredentials(normalizedUsername, resolvedPassword)
    }
}
