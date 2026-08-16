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
import androidx.activity.compose.setContent
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
import kotlin.math.abs
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

private enum class FocusMode(val label: String) {
    AUTO("Prompt / Auto"),
    WHOLE("Whole photo"),
    CENTER("Center / midsection"),
    SKIN("Human skin + contour"),
    GARMENT("Garment / apparel")
}

private data class Controls(
    val prompt: String = "",
    val smartAuto: Boolean = true,
    val bodyAware: Boolean = true,
    val focusMode: FocusMode = FocusMode.AUTO,
    val shadowRecovery: Float = 0.30f,
    val highlightProtection: Float = 0.62f,
    val warmth: Float = 0.03f,
    val saturation: Float = 1.07f,
    val contrast: Float = 1.08f,
    val clarity: Float = 0.34f,
    val contourBoost: Float = 0.30f,
    val deblur: Float = 0.28f,
    val denoise: Float = 0.16f,
    val outputLongSide: Float = 4096f,
)

@OptIn(ExperimentalMaterial3Api::class)
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
            status = "Photo loaded • Ready for body-aware local enhancement"
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("NO LIMITS • LOCAL v0.3") }) }) { pad ->
        Column(
            modifier = Modifier.padding(pad).padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Body-Aware Production Enhancer", style = MaterialTheme.typography.titleMedium)
            Text(
                "Local enhancement can now protect skin, emphasize human contours, and concentrate processing on a prompt-selected region. No Internet permission is used.",
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
                placeholder = { Text("Focus on the midsection of the shorts. Improve garment contour, seams and fabric detail, protect skin, keep it natural.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            SwitchRow("Smart prompt + automatic tonal analysis", controls.smartAuto) {
                controls = controls.copy(smartAuto = it)
            }
            SwitchRow("Human skin + contour awareness", controls.bodyAware) {
                controls = controls.copy(bodyAware = it)
            }

            Text("Processing focus", style = MaterialTheme.typography.labelLarge)
            FocusMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = controls.focusMode == mode, onClick = { controls = controls.copy(focusMode = mode) })
                    Text(mode.label)
                }
            }

            LabeledSlider("Shadow recovery", controls.shadowRecovery, 0f..1f) { controls = controls.copy(shadowRecovery = it) }
            LabeledSlider("Highlight protection", controls.highlightProtection, 0f..1f) { controls = controls.copy(highlightProtection = it) }
            LabeledSlider("Warmth", controls.warmth, -0.35f..0.35f) { controls = controls.copy(warmth = it) }
            LabeledSlider("Color depth", controls.saturation, 0.7f..1.4f) { controls = controls.copy(saturation = it) }
            LabeledSlider("Contrast", controls.contrast, 0.8f..1.25f) { controls = controls.copy(contrast = it) }
            LabeledSlider("Clarity", controls.clarity, 0f..0.8f) { controls = controls.copy(clarity = it) }
            LabeledSlider("Human / garment contour boost", controls.contourBoost, 0f..0.75f) { controls = controls.copy(contourBoost = it) }
            LabeledSlider("Motion-softness cleanup", controls.deblur, 0f..0.7f) { controls = controls.copy(deblur = it) }
            LabeledSlider("Noise cleanup", controls.denoise, 0f..0.5f) { controls = controls.copy(denoise = it) }
            LabeledSlider("Output long side", controls.outputLongSide, 1920f..4096f) { controls = controls.copy(outputLongSide = it) }

            Text(
                "4K production mode preserves substantially more detail than the previous 2560 px limit. This stage is still a faithful enhancer: it does not fabricate missing anatomy or replace clothing. Generative outfit replacement requires a separate local model engine.",
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
                            status = "Finished • 100% on-device"
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
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 520.dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
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

        val targetLong = controls.outputLongSide.roundToInt().coerceIn(1920, 4096)
        val working = resizeForProcessing(original, targetLong)
        if (working !== original) original.recycle()

        val w = working.width
        val h = working.height
        status("Reading tone, skin and contour information...")
        val pixels = IntArray(w * h)
        working.getPixels(pixels, 0, w, 0, 0, w, h)
        val stats = analyze(pixels)
        val focus = buildFocusMask(pixels, w, h, controls)
        val skin = if (controls.bodyAware) buildSkinMask(pixels, w, h) else null

        status("Recovering tone and color...")
        tonalPass(pixels, controls, stats, focus, skin)

        status("Cleaning noise while protecting edges...")
        if (controls.denoise > 0.01f) denoisePass(pixels, w, h, controls.denoise, focus)

        status("Restoring local detail and contours...")
        if (controls.clarity > 0.01f || controls.deblur > 0.01f || controls.contourBoost > 0.01f) {
            clarityPass(pixels, w, h, controls.clarity, controls.deblur, controls.contourBoost, focus, skin)
        }

        status("Rendering high-quality result...")
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        working.recycle()
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 98, bos)
        out.recycle()
        return bos.toByteArray()
    }

    private fun applyPrompt(c: Controls): Controls {
        val p = c.prompt.lowercase()
        var out = c
        if (p.contains("shadow") || p.contains("dark face") || p.contains("dark area")) out = out.copy(shadowRecovery = max(out.shadowRecovery, 0.55f))
        if (p.contains("blur") || p.contains("motion") || p.contains("soft")) out = out.copy(deblur = max(out.deblur, 0.38f), clarity = max(out.clarity, 0.38f))
        if (p.contains("clarity") || p.contains("clear") || p.contains("detail") || p.contains("sharp")) out = out.copy(clarity = max(out.clarity, 0.42f))
        if (p.contains("contour") || p.contains("shape") || p.contains("fit") || p.contains("seam") || p.contains("fabric")) out = out.copy(contourBoost = max(out.contourBoost, 0.42f), clarity = max(out.clarity, 0.36f))
        if (p.contains("warm")) out = out.copy(warmth = max(out.warmth, 0.10f))
        if (p.contains("cool")) out = out.copy(warmth = min(out.warmth, -0.08f))
        if (p.contains("color") || p.contains("depth") || p.contains("vibrant")) out = out.copy(saturation = max(out.saturation, 1.10f))
        if (p.contains("noise") || p.contains("grain")) out = out.copy(denoise = max(out.denoise, 0.24f))
        if (p.contains("skin") || p.contains("body") || p.contains("human")) out = out.copy(bodyAware = true)
        if (p.contains("shorts") || p.contains("garment") || p.contains("clothing") || p.contains("apparel")) out = out.copy(focusMode = FocusMode.GARMENT)
        if (p.contains("midsection") || p.contains("waist") || p.contains("center")) out = out.copy(focusMode = FocusMode.CENTER)
        if (p.contains("skin") && !p.contains("shorts") && !p.contains("garment")) out = out.copy(focusMode = FocusMode.SKIN)
        if (p.contains("natural") || p.contains("realistic") || p.contains("photoreal")) {
            out = out.copy(saturation = min(out.saturation, 1.12f), contrast = min(out.contrast, 1.10f), clarity = min(out.clarity, 0.50f), deblur = min(out.deblur, 0.46f), contourBoost = min(out.contourBoost, 0.50f))
        }
        return out
    }

    private data class Stats(val low: Float, val high: Float, val mean: Float)

    private fun analyze(pixels: IntArray): Stats {
        val hist = IntArray(256)
        var sum = 0L
        for (p in pixels) {
            val y = luminance(Color.red(p), Color.green(p), Color.blue(p)).roundToInt().coerceIn(0, 255)
            hist[y]++; sum += y
        }
        val total = pixels.size
        val lowTarget = (total * 0.01).roundToInt(); val highTarget = (total * 0.99).roundToInt()
        var cumulative = 0; var low = 0; var high = 255
        for (i in hist.indices) { cumulative += hist[i]; if (cumulative >= lowTarget) { low = i; break } }
        cumulative = 0
        for (i in hist.indices) { cumulative += hist[i]; if (cumulative >= highTarget) { high = i; break } }
        return Stats(low.toFloat(), high.toFloat(), sum.toFloat() / total.coerceAtLeast(1))
    }

    private fun buildFocusMask(pixels: IntArray, w: Int, h: Int, c: Controls): FloatArray? {
        val mode = c.focusMode
        if (mode == FocusMode.WHOLE) return null
        if (mode == FocusMode.SKIN) return buildSkinMask(pixels, w, h)
        val mask = FloatArray(w * h)
        val p = c.prompt.lowercase()
        val effective = if (mode == FocusMode.AUTO) {
            when {
                p.contains("shorts") || p.contains("garment") || p.contains("clothing") || p.contains("apparel") -> FocusMode.GARMENT
                p.contains("midsection") || p.contains("waist") || p.contains("center") -> FocusMode.CENTER
                p.contains("skin") || p.contains("body") -> FocusMode.SKIN
                else -> FocusMode.WHOLE
            }
        } else mode
        if (effective == FocusMode.WHOLE) return null
        if (effective == FocusMode.SKIN) return buildSkinMask(pixels, w, h)

        // Soft elliptical region: avoids hard processing boundaries and is intentionally conservative.
        val cx = w * 0.50f
        val cy = if (effective == FocusMode.GARMENT) h * 0.58f else h * 0.50f
        val rx = w * if (effective == FocusMode.GARMENT) 0.42f else 0.36f
        val ry = h * if (effective == FocusMode.GARMENT) 0.31f else 0.28f
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x - cx) / rx; val dy = (y - cy) / ry
            val d = dx*dx + dy*dy
            mask[y*w+x] = ((1.35f - d) / 0.75f).coerceIn(0f, 1f)
        }
        return mask
    }

    // Conservative multi-range skin likelihood. This does not identify identity; it only protects/enhances likely skin-toned pixels.
    private fun buildSkinMask(pixels: IntArray, w: Int, h: Int): FloatArray {
        val raw = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            val maxc = max(r, max(g,b)); val minc = min(r, min(g,b))
            val rgbRule = r > 55 && g > 35 && b > 20 && (maxc-minc) > 12 && r > g*0.92 && r > b*1.03
            val y = 0.299*r + 0.587*g + 0.114*b
            val cb = 128.0 - 0.168736*r - 0.331264*g + 0.5*b
            val cr = 128.0 + 0.5*r - 0.418688*g - 0.081312*b
            val yccRule = y > 35 && cb in 72.0..142.0 && cr in 128.0..183.0
            raw[i] = if (rgbRule && yccRule) 1f else 0f
        }
        // Cheap spatial smoothing makes the mask follow broad skin contours rather than individual pixels.
        val smooth = raw.copyOf()
        for (y in 1 until h-1) for (x in 1 until w-1) {
            var s=0f
            for (yy in y-1..y+1) for (xx in x-1..x+1) s += raw[yy*w+xx]
            smooth[y*w+x] = (s/9f).coerceIn(0f,1f)
        }
        return smooth
    }

    private fun tonalPass(pixels: IntArray, c: Controls, s: Stats, focus: FloatArray?, skin: FloatArray?) {
        val span = max(32f, s.high - s.low)
        val autoExposure = ((118f - s.mean) / 255f).coerceIn(-0.12f, 0.16f)
        for (i in pixels.indices) {
            val p = pixels[i]
            var r = Color.red(p).toFloat(); var g = Color.green(p).toFloat(); var b = Color.blue(p).toFloat(); val a = Color.alpha(p)
            val lum0 = luminance(r.toInt(), g.toInt(), b.toInt()) / 255f
            val local = focus?.get(i) ?: 1f
            val skinProtect = skin?.get(i) ?: 0f
            val localContrast = 1f + (c.contrast - 1f) * (0.45f + 0.55f*local) * (1f - 0.22f*skinProtect)

            fun stretch(v: Float): Float = ((v - s.low) * (255f / span)).coerceIn(0f, 255f)
            r = mix(r, stretch(r), 0.18f); g = mix(g, stretch(g), 0.18f); b = mix(b, stretch(b), 0.18f)
            val shadowMask = ((0.62f - lum0) / 0.62f).coerceIn(0f, 1f).pow(1.35f)
            val lift = (c.shadowRecovery * shadowMask * 58f) + (autoExposure * 255f * (0.35f + 0.65f * shadowMask))
            r += lift; g += lift; b += lift

            val lum1 = luminance(r.toInt(), g.toInt(), b.toInt()) / 255f
            if (lum1 > 0.72f) {
                val m = ((lum1 - 0.72f) / 0.28f).coerceIn(0f, 1f) * c.highlightProtection
                val compress = 1f - 0.18f*m
                r = 255f - (255f-r)*compress; g = 255f - (255f-g)*compress; b = 255f - (255f-b)*compress
            }

            r *= (1f + c.warmth * 0.18f); g *= (1f + c.warmth * 0.03f); b *= (1f - c.warmth * 0.18f)
            val gray = luminance(r.toInt(), g.toInt(), b.toInt())
            val sat = 1f + (c.saturation-1f)*(0.65f+0.35f*local)*(1f-0.15f*skinProtect)
            r = gray + (r-gray)*sat; g = gray + (g-gray)*sat; b = gray + (b-gray)*sat
            r = 128f + (r-128f)*localContrast; g = 128f + (g-128f)*localContrast; b = 128f + (b-128f)*localContrast
            pixels[i] = Color.argb(a, clamp(r), clamp(g), clamp(b))
        }
    }

    private fun denoisePass(pixels: IntArray, w: Int, h: Int, amount: Float, focus: FloatArray?) {
        val src = pixels.copyOf()
        for (y in 1 until h-1) for (x in 1 until w-1) {
            val idx = y*w+x; val center=src[idx]
            val cr=Color.red(center); val cg=Color.green(center); val cb=Color.blue(center)
            var sr=0; var sg=0; var sb=0; var count=0
            for (yy in y-1..y+1) for (xx in x-1..x+1) {
                val p=src[yy*w+xx]; val r=Color.red(p); val g=Color.green(p); val b=Color.blue(p)
                if (abs(r-cr)+abs(g-cg)+abs(b-cb) < 95) { sr+=r; sg+=g; sb+=b; count++ }
            }
            if (count>0) {
                val local = focus?.get(idx) ?: 1f
                val blend = (amount*0.55f*(1f-0.25f*local)).coerceIn(0f,0.35f)
                pixels[idx]=Color.argb(Color.alpha(center), clamp(mix(cr.toFloat(),sr.toFloat()/count,blend)), clamp(mix(cg.toFloat(),sg.toFloat()/count,blend)), clamp(mix(cb.toFloat(),sb.toFloat()/count,blend)))
            }
        }
    }

    private fun clarityPass(pixels: IntArray, w: Int, h: Int, clarity: Float, deblur: Float, contourBoost: Float, focus: FloatArray?, skin: FloatArray?) {
        val src = pixels.copyOf(); val br=IntArray(src.size); val bg=IntArray(src.size); val bb=IntArray(src.size)
        for (y in 1 until h-1) for (x in 1 until w-1) {
            val ids=intArrayOf(y*w+x,y*w+x-1,y*w+x+1,(y-1)*w+x,(y+1)*w+x)
            var rr=0;var gg=0;var bbb=0
            for(id in ids){val p=src[id];rr+=Color.red(p);gg+=Color.green(p);bbb+=Color.blue(p)}
            val id=y*w+x;br[id]=rr/5;bg[id]=gg/5;bb[id]=bbb/5
        }
        val baseStrength=(clarity*0.72f+deblur*1.02f).coerceIn(0f,1.0f)
        for(y in 1 until h-1) for(x in 1 until w-1){
            val id=y*w+x;val p=src[id];val r=Color.red(p);val g=Color.green(p);val b=Color.blue(p)
            val edge=abs(r-br[id])+abs(g-bg[id])+abs(b-bb[id]);val edgeMask=(edge/78f).coerceIn(0.10f,1f)
            val local=focus?.get(id)?:1f; val skinAmount=skin?.get(id)?:0f
            val contour = contourBoost * local * edgeMask * (1f - 0.35f*skinAmount)
            val strength=(baseStrength*(0.35f+0.65f*local)+contour*0.70f).coerceIn(0f,1.18f)
            pixels[id]=Color.argb(Color.alpha(p),clamp(r+(r-br[id])*strength),clamp(g+(g-bg[id])*strength),clamp(b+(b-bb[id])*strength))
        }
    }

    private fun resizeForProcessing(src: Bitmap, targetLong: Int): Bitmap {
        val longSide=max(src.width,src.height)
        if(longSide==targetLong) return src.copy(Bitmap.Config.ARGB_8888,false)
        // Never exceed 1.6x synthetic enlargement in this non-generative stage; larger values would only create bigger soft pixels.
        val scale=if(longSide>targetLong) targetLong.toFloat()/longSide else min(1.60f,targetLong.toFloat()/longSide)
        val nw=max(1,(src.width*scale).roundToInt());val nh=max(1,(src.height*scale).roundToInt())
        return Bitmap.createScaledBitmap(src,nw,nh,true).copy(Bitmap.Config.ARGB_8888,false)
    }

    private fun luminance(r:Int,g:Int,b:Int):Float=0.2126f*r+0.7152f*g+0.0722f*b
    private fun mix(a:Float,b:Float,t:Float):Float=a+(b-a)*t
    private fun clamp(v:Float):Int=v.roundToInt().coerceIn(0,255)
}

private fun saveToGallery(context: Context, bytes: ByteArray) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "NoLimits_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/No Limits")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Could not create gallery file")
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
}
