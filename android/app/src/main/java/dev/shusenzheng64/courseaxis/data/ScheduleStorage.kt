package dev.shusenzheng64.courseaxis.data

import android.content.Context
import dev.shusenzheng64.courseaxis.model.ScheduleDocument
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

object ScheduleStorage {
    private const val NAME = "schedule.json"

    fun read(context: Context): ScheduleDocument {
        val file = File(context.filesDir, NAME)
        if (!file.exists()) return ScheduleDocument.empty()
        val migration = runCatching { ScheduleJsonCodec.decodeWithMigration(file.readText()) }.getOrElse {
            runCatching {
                file.copyTo(
                    File(context.filesDir, "schedule.corrupt-${Instant.now().epochSecond}.json"),
                    overwrite = true,
                )
            }
            return ScheduleDocument.empty()
        }
        if (migration.changed) runCatching { write(context, migration.document) }
        return migration.document
    }

    fun write(context: Context, document: ScheduleDocument) {
        val target = File(context.filesDir, NAME)
        val temp = File(context.filesDir, "$NAME.tmp")
        FileOutputStream(temp).use {
            it.write(ScheduleJsonCodec.encode(document, 2).toByteArray())
            it.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun clear(context: Context) {
        context.filesDir.listFiles()
            .orEmpty()
            .filter { file ->
                file.name == NAME ||
                    file.name == "$NAME.tmp" ||
                    file.name.startsWith("schedule.corrupt-")
            }
            .forEach { file -> check(file.delete() || !file.exists()) { "无法清除本机课表数据" } }
    }
}
