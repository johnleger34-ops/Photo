package com.nolimits.photoeditor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { NoLimitsApp(this) } }
    }
}

private data class Controls(
    val prompt: String = "",
    val smartAuto: Boolean = true,
    val shadowRecovery: Float = 0.35f,
    val highlightProtection: Float = 0.55f,
    val warmth: Float = 0.06f,
    val saturation: Float = 1.06f,
    val contrast: Float = 1.04f,
    val clarity: Float = 0.28f,
    val deblur: Float = 0.22f,
    val denoise: Float = 0.14f,
    val outputLongSide: Float = 1920f,
)

@Composable
private fun NoLimitsApp(context: Context) {
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var resultBytes by remember { mutableStateOf<ByteArray?>(null) }
    var controls by remember { mutableStateOf(Controls()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("100% local • No cloud • No upload") }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            sourceUri = uri
            sourceBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            resultBytes = null
            status = "Photo loaded • Ready for local enhancement"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("NO LIMITS • LOCAL") }) }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Private On-Device Photo Enhancer", style = MaterialTheme.typography.titleMedium)
            Text(
                "Everything runs on this phone. The app has no Internet permission and does not send photos anywhere.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("Choose Photo") }
                OutlinedButton(
                    onClick = {
                        resultBytes?.let {
                            saveToGallery(context, it)
                            status = "Saved to Pictures/No Limits"
                        }
                    },
                    enabled = resultBytes != null && !busy
                ) { Text("Save Result") }
            }

            sourceBytes?.let { ImagePanel("Original", it) }
            resultBytes?.let { ImagePanel("Enhanced", it) }

            OutlinedTextField(
                value = controls.prompt,
                onValueChange = { controls = controls.copy(prompt = it) },
                label = { Text("Tell No Limits what needs attention") },
                placeholder = { Text("Lift the shadow on the face, reduce blur, add a little warmth and color depth, keep it natural") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            SwitchRow("Smart prompt + automatic tonal analysis", controls.smartAuto) {
                controls = controls.copy(smartAuto = it)
            }
            LabeledSlider("Shadow recovery", controls.shadowRecovery, 0f..1f) { controls = controls.copy(shadowRecovery = it) }
            LabeledSlider("Highlight protection", controls.highlightProtection, 0f..1f) { controls = controls.copy(highlightProtection = it) }
            LabeledSlider("Warmth", controls.warmth, -0.35f..0.35f) { controls = controls.copy(warmth = it) }
            LabeledSlider("Color depth", controls.saturation, 0.7f..1.4f) { controls = controls.copy(saturation = it) }
            LabeledSlider("Contrast", controls.contrast, 0.8f..1.25f) { controls = controls.copy(contrast = it) }
            LabeledSlider("Clarity", controls.clarity, 0f..0.8f) { controls = controls.copy(clarity = it) }
            LabeledSlider("Motion-softness cleanup", controls.deblur, 0f..0.7f) { controls = controls.copy(deblur = it) }
            LabeledSlider("Noise cleanup", controls.denoise, 0f..0.5f) { controls = controls.copy(denoise = it) }
            LabeledSlider("Output long side", controls.outputLongSide, 1080f..2560f) { controls = controls.copy(outputLongSide = it) }

            Text(
                "This build enhances information already in the photo: shadows, color, contrast, noise and edge clarity. It does not invent missing faces or objects.",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = {
                    val input = sourceBytes ?: return@Button
                    scope.launch {
                        busy = true
                        status = "Analyzing photo locally..."
                        try {
                            resultBytes = withContext(Dispatchers.Default) {
                                LocalEnhancer.enhance(input, controls) { status = it }
                            }
                            status = "Finished • Processed entirely on this phone"
                        } catch (e: Exception) {
                            status = "Error: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = sourceUri != null && sourceBytes != null && !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(if (busy) "WORKING LOCALLY..." else "ENHANCE PHOTO") }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImagePanel(label: String, bytes: ByteArray) {
    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    Card(shape = RoundedCornerShape(16.dp)) {
        Column {
            Text(label, Modifier.padding(10.dp), style = MaterialTheme.typography.labelLarge)
            Image(
                bmp.asImageBitmap(),
                null,
                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(if (label.contains("Output")) value.roundToInt().toString() else "%.2f".format(value))
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private object LocalEnhancer {
    fun enhance(input: ByteArray, base: Controls, status: (String) -> Unit): ByteArray {
        status("Decoding photo...")
        val original = BitmapFactory.decodeByteArray(input, 0, input.size) ?: error("Could not decode photo")
        val controls = if (base.smartAuto) applyPrompt(base) else base

        val targetLong = controls.outputLongSide.roundToInt().coerceIn(1080, 2560)
        val working = resizeForProcessing(original, targetLong)
        if (working !== original) original.recycle()

        status("Analyzing light and shadows...")
        val pixels = IntArray(working.width * working.height)
        working.getPixels(pixels, 0, working.width, 0, 0, working.width, working.height)
        val stats = analyze(pixels)

        status("Recovering tone and color...")
        tonalPass(pixels, controls, stats)

        status("Cleaning noise...")
        if (controls.denoise > 0.01f) denoisePass(pixels, working.width, working.height, controls.denoise)

        status("Restoring clarity...")
        if (controls.clarity > 0.01f || controls.deblur > 0.01f) {
            clarityPass(pixels, working.width, working.height, controls.clarity, controls.deblur)
        }

        status("Rendering final image...")
        val out = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, working.width, 0, 0, working.width, working.height)
        working.recycle()

        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 96, bos)
        out.recycle()
        return bos.toByteArray()
    }

    private fun applyPrompt(c: Controls): Controls {
        val p = c.prompt.lowercase()
        var out = c
        if (p.contains("shadow") || p.contains("dark face") || p.contains("dark area")) {
            out = out.copy(shadowRecovery = max(out.shadowRecovery, 0.55f))
        }
        if (p.contains("blur") || p.contains("motion") || p.contains("soft")) {
            out = out.copy(deblur = max(out.deblur, 0.38f), clarity = max(out.clarity, 0.38f))
        }
        if (p.contains("clarity") || p.contains("clear") || p.contains("detail") || p.contains("sharp")) {
            out = out.copy(clarity = max(out.clarity, 0.42f))
        }
        if (p.contains("warm")) out = out.copy(warmth = max(out.warmth, 0.10f))
        if (p.contains("cool")) out = out.copy(warmth = min(out.warmth, -0.08f))
        if (p.contains("color") || p.contains("depth") || p.contains("vibrant")) {
            out = out.copy(saturation = max(out.saturation, 1.10f))
        }
        if (p.contains("noise") || p.contains("grain")) out = out.copy(denoise = max(out.denoise, 0.24f))
        if (p.contains("natural") || p.contains("realistic") || p.contains("photoreal")) {
            out = out.copy(
                saturation = min(out.saturation, 1.12f),
                contrast = min(out.contrast, 1.08f),
                clarity = min(out.clarity, 0.48f),
                deblur = min(out.deblur, 0.45f)
            )
        }
        return out
    }

    private data class Stats(val low: Float, val high: Float, val mean: Float)

    private fun analyze(pixels: IntArray): Stats {
        val hist = IntArray(256)
        var sum = 0L
        for (p in pixels) {
            val y = luminance(Color.red(p), Color.green(p), Color.blue(p)).roundToInt().coerceIn(0, 255)
            hist[y]++
            sum += y
        }
        val total = pixels.size
        val lowTarget = (total * 0.01).roundToInt()
        val highTarget = (total * 0.99).roundToInt()
        var cumulative = 0
        var low = 0
        var high = 255
        for (i in hist.indices) {
            cumulative += hist[i]
            if (cumulative >= lowTarget) { low = i; break }
        }
        cumulative = 0
        for (i in hist.indices) {
            cumulative += hist[i]
            if (cumulative >= highTarget) { high = i; break }
        }
        return Stats(low.toFloat(), high.toFloat(), sum.toFloat() / total.coerceAtLeast(1))
    }

    private fun tonalPass(pixels: IntArray, c: Controls, s: Stats) {
        val span = max(32f, s.high - s.low)
        val autoExposure = ((118f - s.mean) / 255f).coerceIn(-0.12f, 0.16f)
        for (i in pixels.indices) {
            val p = pixels[i]
            var r = Color.red(p).toFloat()
            var g = Color.green(p).toFloat()
            var b = Color.blue(p).toFloat()
            val a = Color.alpha(p)
            val lum0 = luminance(r.toInt(), g.toInt(), b.toInt()) / 255f

            // Gentle percentile stretch instead of a destructive global auto-level.
            fun stretch(v: Float): Float = ((v - s.low) * (255f / span)).coerceIn(0f, 255f)
            r = mix(r, stretch(r), 0.20f)
            g = mix(g, stretch(g), 0.20f)
            b = mix(b, stretch(b), 0.20f)

            // Selective shadow lift: strongest below midtones, fades to zero in highlights.
            val shadowMask = ((0.62f - lum0) / 0.62f).coerceIn(0f, 1f).pow(1.35f)
            val lift = (c.shadowRecovery * shadowMask * 58f) + (autoExposure * 255f * (0.35f + 0.65f * shadowMask))
            r += lift; g += lift; b += lift

            // Highlight protection compresses only the brightest values.
            val lum1 = luminance(r.toInt(), g.toInt(), b.toInt()) / 255f
            if (lum1 > 0.72f) {
                val mask = ((lum1 - 0.72f) / 0.28f).coerceIn(0f, 1f) * c.highlightProtection
                val compress = 1f - 0.18f * mask
                r = 255f - (255f - r) * compress
                g = 255f - (255f - g) * compress
                b = 255f - (255f - b) * compress
            }

            // Warmth is channel-balanced so neutral areas stay believable.
            r *= (1f + c.warmth * 0.18f)
            g *= (1f + c.warmth * 0.03f)
            b *= (1f - c.warmth * 0.18f)

            val gray = luminance(r.toInt(), g.toInt(), b.toInt())
            r = gray + (r - gray) * c.saturation
            g = gray + (g - gray) * c.saturation
            b = gray + (b - gray) * c.saturation

            r = 128f + (r - 128f) * c.contrast
            g = 128f + (g - 128f) * c.contrast
            b = 128f + (b - 128f) * c.contrast

            pixels[i] = Color.argb(a, clamp(r), clamp(g), clamp(b))
        }
    }

    private fun denoisePass(pixels: IntArray, w: Int, h: Int, amount: Float) {
        val src = pixels.copyOf()
        val blend = (amount * 0.55f).coerceIn(0f, 0.35f)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var sr = 0; var sg = 0; var sb = 0; var count = 0
                val center = src[y * w + x]
                val cr = Color.red(center); val cg = Color.green(center); val cb = Color.blue(center)
                for (yy in y - 1..y + 1) for (xx in x - 1..x + 1) {
                    val p = src[yy * w + xx]
                    val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                    val diff = kotlin.math.abs(r-cr) + kotlin.math.abs(g-cg) + kotlin.math.abs(b-cb)
                    if (diff < 95) { sr += r; sg += g; sb += b; count++ }
                }
                if (count > 0) {
                    val idx = y * w + x
                    pixels[idx] = Color.argb(
                        Color.alpha(center),
                        clamp(mix(cr.toFloat(), sr.toFloat()/count, blend)),
                        clamp(mix(cg.toFloat(), sg.toFloat()/count, blend)),
                        clamp(mix(cb.toFloat(), sb.toFloat()/count, blend))
                    )
                }
            }
        }
    }

    private fun clarityPass(pixels: IntArray, w: Int, h: Int, clarity: Float, deblur: Float) {
        val src = pixels.copyOf()
        val blurR = IntArray(src.size)
        val blurG = IntArray(src.size)
        val blurB = IntArray(src.size)
        // Fast cross blur: enough to estimate local detail without costly kernels on-phone.
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val ids = intArrayOf(y*w+x, y*w+x-1, y*w+x+1, (y-1)*w+x, (y+1)*w+x)
                var rr=0; var gg=0; var bb=0
                for (id in ids) { val p=src[id]; rr+=Color.red(p); gg+=Color.green(p); bb+=Color.blue(p) }
                val id = y*w+x
                blurR[id]=rr/5; blurG[id]=gg/5; blurB[id]=bb/5
            }
        }
        val strength = (clarity * 0.75f + deblur * 1.10f).coerceIn(0f, 1.05f)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val id = y*w+x
                val p = src[id]
                val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                val edge = kotlin.math.abs(r-blurR[id]) + kotlin.math.abs(g-blurG[id]) + kotlin.math.abs(b-blurB[id])
                // Avoid sharpening smooth/noisy areas too aggressively.
                val edgeMask = (edge / 75f).coerceIn(0.18f, 1f)
                val s = strength * edgeMask
                pixels[id] = Color.argb(
                    Color.alpha(p),
                    clamp(r + (r - blurR[id]) * s),
                    clamp(g + (g - blurG[id]) * s),
                    clamp(b + (b - blurB[id]) * s)
                )
            }
        }
    }

    private fun resizeForProcessing(src: Bitmap, targetLong: Int): Bitmap {
        val longSide = max(src.width, src.height)
        if (longSide == targetLong) return src.copy(Bitmap.Config.ARGB_8888, false)
        val scale = if (longSide > targetLong) targetLong.toFloat() / longSide else min(1.35f, targetLong.toFloat() / longSide)
        val nw = max(1, (src.width * scale).roundToInt())
        val nh = max(1, (src.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(src, nw, nh, true).copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun luminance(r: Int, g: Int, b: Int): Float = 0.2126f*r + 0.7152f*g + 0.0722f*b
    private fun mix(a: Float, b: Float, t: Float): Float = a + (b-a)*t
    private fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, 255)
}

private fun saveToGallery(context: Context, bytes: ByteArray) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "NoLimits_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/No Limits")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Could not create gallery file")
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
}
