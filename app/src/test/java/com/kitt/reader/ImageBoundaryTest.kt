package com.kitt.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.ByteArrayInputStream
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageBoundaryTest {
    @Test fun decodeBoundsAndReencodeRemovesExifLocation() {
        val app = RuntimeEnvironment.getApplication(); val file = File(app.cacheDir, "image-reader-test.jpg")
        val bitmap = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "30/1,0/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N"); exif.saveAttributes()
            val image = AndroidImageReader.read(app, Uri.fromFile(file))
            val bytes = Base64.getDecoder().decode(image.dataUrl.substringAfter(','))
            val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertTrue(result.width <= 1536 && result.height <= 1536); result.recycle()
            assertNull(ExifInterface(ByteArrayInputStream(bytes)).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertTrue(bytes.size <= 1_500_000)
        } finally { bitmap.recycle(); file.delete() }
    }
    @Test fun malformedAndOversizedFilesAreRejectedBeforeProvider() {
        val app = RuntimeEnvironment.getApplication(); val file = File(app.cacheDir, "bad-image-test.jpg")
        try {
            file.writeText("not an image")
            assertTrue(runCatching { AndroidImageReader.read(app, Uri.fromFile(file)) }.isFailure)
            file.outputStream().use { output -> repeat(17) { output.write(ByteArray(1_000_000)) } }
            assertTrue(runCatching { AndroidImageReader.read(app, Uri.fromFile(file)) }.isFailure)
        } finally { file.delete() }
    }
    @Test fun cameraProviderDeclaresOnlyCaptureCacheWithoutCameraOrStoragePermission() {
        val app = RuntimeEnvironment.getApplication()
        val file = File(app.cacheDir, "visual-talk/capture.jpg"); file.parentFile!!.mkdirs(); file.writeText("test")
        try {
            // AndroidX assumes '/' canonical paths; its URI-grant behavior is a phone gate on Windows JVM.
            val paths = app.resources.getXml(R.xml.image_paths)
            val roots = mutableListOf<Pair<String, String>>()
            while (paths.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (paths.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && paths.name != "paths")
                    roots += paths.name to paths.getAttributeValue(null, "path")
            }
            paths.close(); assertEquals(listOf("cache-path" to "visual-talk/"), roots)
            val info = app.packageManager.getProviderInfo(android.content.ComponentName(app, FileProvider::class.java), 0)
            assertFalse(info.exported); assertTrue(info.grantUriPermissions)
            val permissions = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            assertFalse(permissions.contains(android.Manifest.permission.CAMERA)); assertFalse(permissions.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        } finally { file.delete() }
    }
    @Test fun runtimeEndAndServiceLossClearRouteVisualCacheAndNeverPersistMedia() {
        val app = RuntimeEnvironment.getApplication(); val runtime = KittRuntime(app, TestVoice())
        try {
            runtime.start(simulated = true); runtime.pipeline.routeHint = "本次路线提示"
            runtime.visualTalk.begin()
            val capture = File(app.cacheDir, "visual-talk/capture.jpg"); capture.parentFile!!.mkdirs(); capture.writeText("temporary")
            runtime.checkpoint()
            val recovery = File(app.filesDir, "trips/current.json").readText()
            assertFalse(recovery.contains("路线提示")); assertFalse(recovery.contains("image")); assertFalse(recovery.contains("capture"))
            runtime.end(); assertFalse(capture.exists()); assertFalse(runtime.visualTalk.open)
            assertEquals("", runtime.pipeline.routeHint); assertEquals(0, runtime.pipeline.areas.size)
            runtime.start(simulated = true); runtime.pipeline.routeHint = "第二次路线"; runtime.visualTalk.begin(); runtime.serviceLost()
            assertFalse(runtime.visualTalk.open); assertEquals("", runtime.pipeline.routeHint)
        } finally { runtime.end(); runtime.scope.cancel() }
    }
}
