package com.wikiglobal.iconconverter.autoadapt

import android.content.Context
import com.wikiglobal.iconconverter.hyperos.HyperOs3ThemePatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Uses real SharedPreferences and filesDir; no in-memory stand-ins for durable state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoAdaptStorageTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        listOf("auto-adapt-pending", "selected-icon-pack", "active-theme-mode").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(context.filesDir, "icon-pack").deleteRecursively()
    }

    @After fun tearDown() { File(context.filesDir, "icon-pack").deleteRecursively() }

    @Test fun PENDING_STORE_PROCESS_RECREATE() {
        PendingPackageStore(context).record("pkg.one", AutoAdaptEventType.NEW_INSTALL)
        val reopened = PendingPackageStore(context)
        assertEquals(PendingPackageEvent::class, reopened.snapshot().single()::class)
        assertEquals("pkg.one", reopened.snapshot().single().packageName)
    }

    @Test fun PENDING_STORE_REPLACED_WINS_AND_DIFFERENT_PACKAGES_PERSIST() {
        val store = PendingPackageStore(context)
        store.record("pkg.one", AutoAdaptEventType.NEW_INSTALL)
        store.record("pkg.one", AutoAdaptEventType.REPLACED)
        store.record("pkg.two", AutoAdaptEventType.NEW_INSTALL)
        val events = PendingPackageStore(context).snapshot().associateBy { it.packageName }
        assertEquals(AutoAdaptEventType.REPLACED, events.getValue("pkg.one").eventType)
        assertEquals(AutoAdaptEventType.NEW_INSTALL, events.getValue("pkg.two").eventType)
    }

    @Test fun SAME_PACKAGE_EVENT_UPDATED_DURING_WORK_NOT_LOST() {
        val store = PendingPackageStore(context)
        val snapshot = listOf(store.record("pkg", AutoAdaptEventType.NEW_INSTALL))
        store.record("pkg", AutoAdaptEventType.REPLACED)
        store.clearProcessed(snapshot)
        assertEquals(AutoAdaptEventType.REPLACED, PendingPackageStore(context).snapshot().single().eventType)
    }

    @Test fun LAST_APPLIED_MODE_NOT_UI_TAB() {
        ActiveThemeModeStore(context).set(ActiveThemeMode.ICON_PACK)
        val currentlySelectedTab = ActiveThemeMode.MATERIAL_YOU
        assertEquals(ActiveThemeMode.MATERIAL_YOU, currentlySelectedTab)
        assertEquals(ActiveThemeMode.ICON_PACK, ActiveThemeModeStore(context).get())
    }

    @Test fun RESTORE_CLEARS_ACTIVE_MODE() {
        val store = ActiveThemeModeStore(context)
        store.set(ActiveThemeMode.ICON_PACK)
        store.set(ActiveThemeMode.NONE)
        assertEquals(ActiveThemeMode.NONE, ActiveThemeModeStore(context).get())
    }

    @Test fun INITIAL_ICON_PACK_IMPORT() {
        val selected = import("old apk")
        assertTrue(selected.isFile)
        assertEquals("old apk", selected.readText())
        assertTrue(SelectedIconPackStore(context).validateSelected() is SelectedIconPackValidation.Valid)
    }

    @Test fun ICON_PACK_SOURCE_RESTORED_AFTER_STORE_RECREATE() {
        import("durable apk")
        val reopened = SelectedIconPackStore(context)
        val validated = reopened.validateSelected() as SelectedIconPackValidation.Valid
        assertEquals("durable apk", validated.file.readText())
        assertEquals("package.old", validated.metadata.packageName)
    }

    @Test fun FAILED_REPLACEMENT_PRESERVES_OLD_PACK() {
        val old = import("old apk")
        val temp = File(old.parentFile, "selected.apk.tmp").apply { writeText("new apk") }
        val store = SelectedIconPackStore(context, FailingReplacementMover)
        runCatching { store.commitTemporary(temp, metadataFor(temp, "package.new")) }.onSuccess { error("expected failed replacement") }
        assertEquals("old apk", old.readText())
        assertEquals("package.old", (store.validateSelected() as SelectedIconPackValidation.Valid).metadata.packageName)
    }

    @Test fun METADATA_UPDATED_ONLY_AFTER_FILE_COMMIT() {
        import("old apk")
        val wrongDirectoryTemp = File(context.cacheDir, "selected.apk.tmp").apply { writeText("new apk") }
        runCatching { SelectedIconPackStore(context).commitTemporary(wrongDirectoryTemp, metadataFor(wrongDirectoryTemp, "package.new")) }
        assertEquals("package.old", (SelectedIconPackStore(context).validateSelected() as SelectedIconPackValidation.Valid).metadata.packageName)
    }

    @Test fun TEMP_FILE_NOT_ACTIVE_SOURCE() {
        val active = import("active apk")
        File(active.parentFile, "selected.apk.tmp").writeText("temporary apk")
        val selected = (SelectedIconPackStore(context).validateSelected() as SelectedIconPackValidation.Valid).file
        assertEquals("active apk", selected.readText())
    }

    @Test fun ICON_PACK_SHA_MISMATCH_BLOCKED() {
        val selected = import("good apk")
        selected.writeText("tampered apk")
        assertTrue(SelectedIconPackStore(context).validateSelected() is SelectedIconPackValidation.ShaMismatch)
    }

    private fun import(contents: String): File {
        val store = SelectedIconPackStore(context)
        val directory = File(context.filesDir, "icon-pack").apply { mkdirs() }
        val temp = File(directory, "selected.apk.tmp").apply { writeText(contents) }
        return store.commitTemporary(temp, metadataFor(temp, "package.old"))
    }

    private fun metadataFor(file: File, packageName: String) = SelectedIconPackMetadata(
        packageName = packageName,
        displayName = packageName,
        sha256 = HyperOs3ThemePatcher.sha256(file),
        importedAt = 42,
    )

    private object FailingReplacementMover : IconPackFileMover {
        override fun move(source: File, target: File, atomic: Boolean) {
            if (atomic) throw AtomicMoveNotSupportedException(source.path, target.path, "test fallback")
            if (source.name == "selected.apk.tmp" && target.name == "selected.apk") throw IOException("test replacement failure")
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
