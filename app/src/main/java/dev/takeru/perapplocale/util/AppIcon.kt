package dev.takeru.perapplocale.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads launcher icons lazily and keeps a bounded cache. Clones intentionally share the
 * package-name cache because they normally install the same APK; row identity never uses it.
 *
 * Decoding every icon up front would cost hundreds of milliseconds and a lot of memory on a
 * device with 300 packages, so rows ask for their own icon as they scroll into view.
 */
private object IconCache {
    private const val ICON_PX = 128
    private val cache = LruCache<String, ImageBitmap>(256)

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache[packageName]?.let { return it }
        return withContext(Dispatchers.IO) {
            val drawable = runCatching {
                context.packageManager.getApplicationIcon(packageName)
            }.getOrNull() ?: return@withContext null
            val bitmap = drawable.toBitmap(ICON_PX).asImageBitmap()
            cache.put(packageName, bitmap)
            bitmap
        }
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bitmap
    }
}

@Composable
fun rememberAppIcon(packageName: String): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = IconCache.load(context, packageName)
    }
}
