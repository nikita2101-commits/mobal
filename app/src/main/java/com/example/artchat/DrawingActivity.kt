package com.example.artchat

import android.app.AlertDialog
import android.graphics.*
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.artchat.databinding.ActivityDrawingBinding
import com.example.artchat.databinding.BrushItemBinding
import com.example.artchat.databinding.ColorItemBinding
import java.text.SimpleDateFormat
import java.util.*

class DrawingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrawingBinding
    private lateinit var drawBitmap: Bitmap
    private lateinit var drawCanvas: Canvas
    private lateinit var paint: Paint
    private var path = Path()
    private var brushSize = 20f
    private var currentColor = Color.BLACK
    private var isEraser = false
    private var uiVisible = true
    private var isAdvancedPaletteVisible = false

    // Хранилище всех нарисованных путей
    private val paths = mutableListOf<DrawingPath>()
    private val undonePaths = mutableListOf<DrawingPath>()

    // Palettes
    private lateinit var colorPaletteAdapter: ColorPaletteAdapter
    private lateinit var shadePaletteAdapter: ShadePaletteAdapter
    private var brushPaletteAdapter: BrushPaletteAdapter? = null

    private val baseColors = listOf(
        Color.BLACK,    // Черный
        Color.RED,      // Красный
        Color.GREEN,    // Зеленый
        Color.BLUE,     // Синий
        Color.YELLOW,   // Желтый
        Color.MAGENTA,  // Пурпурный
        Color.parseColor("#FF9800"), // Оранжевый
        Color.parseColor("#795548"), // Коричневый
        Color.parseColor("#9C27B0"), // Фиолетовый
        Color.parseColor("#E91E63"), // Розовый
        Color.parseColor("#00BCD4"), // Бирюзовый
        Color.WHITE     // Белый
    )

    private var currentBaseColor = Color.BLACK
    private val shades = mutableListOf<Int>()

    // Кисти
    data class BrushItem(
        val type: BrushType,
        val name: String,
        val emoji: String
    )

    enum class BrushType {
        PENCIL, MARKER, PAINT, CRAYON, SPRAY, CHARCOAL, WATERCOLOR,
        AIRBRUSH, OIL_PAINT, INK, PASTEL, GLOW
    }

    private val brushItems = listOf(
        BrushItem(BrushType.PENCIL, "Карандаш", "✏️"),
        BrushItem(BrushType.MARKER, "Маркер", "🖍"),
        BrushItem(BrushType.PAINT, "Краска", "🎨"),
        BrushItem(BrushType.CRAYON, "Мелки", "🖌"),
        BrushItem(BrushType.SPRAY, "Аэрозоль", "💨"),
        BrushItem(BrushType.CHARCOAL, "Уголь", "🔥"),
        BrushItem(BrushType.WATERCOLOR, "Акварель", "💧"),
        BrushItem(BrushType.AIRBRUSH, "Аэрограф", "🎯"),
        BrushItem(BrushType.OIL_PAINT, "Масло", "🛢️"),
        BrushItem(BrushType.INK, "Чернила", "🖋️"),
        BrushItem(BrushType.PASTEL, "Пастель", "🎨"),
        BrushItem(BrushType.GLOW, "Свечение", "🌟")
    )

    private var currentBrushType = BrushType.PENCIL

    data class DrawingPath(
        val path: Path,
        val paint: Paint,
        val brushType: BrushType,
        val color: Int,
        val size: Float
    )

    // Размеры холста
    private var canvasWidth = 0
    private var canvasHeight = 0

    // Переменные для палитры
    private var hue = 0
    private var saturation = 100
    private var brightness = 100
    private var alpha = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            setupPaint()
            setupClickListeners()
            setupBrushListeners()

            // Настраиваем canvas после того, как view будет измерено
            binding.drawingCanvas.post {
                try {
                    setupCanvasSimple()
                    setupPalettes()
                    setupBrushPalette()

                    // Инициализируем отображение текущей кисти и цвета
                    val currentBrushItem = brushItems.find { it.type == currentBrushType }
                    currentBrushItem?.let {
                        binding.tvCurrentBrushIcon.text = it.emoji
                        binding.tvCurrentBrush.text = it.name
                    }
                    binding.currentColorPreview.setBackgroundColor(currentColor)

                    // Обновляем предпросмотр размера кисти
                    updateBrushPreview()

                    setupUndoRedoButtons()

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Ошибка инициализации: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка создания: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupCanvasSimple() {
        try {
            // Фиксированный размер холста
            val metrics = resources.displayMetrics
            canvasWidth = metrics.widthPixels - 100
            canvasHeight = metrics.heightPixels - 400

            drawBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(drawBitmap)

            // Заливаем белым фоном
            drawCanvas.drawColor(Color.WHITE)

            // Устанавливаем bitmap в ImageView
            binding.drawingCanvas.setImageBitmap(drawBitmap)
            binding.drawingCanvas.adjustViewBounds = true
            binding.drawingCanvas.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE

            // Обработка касаний
            binding.drawingCanvas.setOnTouchListener { _, event ->
                handleTouch(event)
                true
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка создания холста: ${e.message}", Toast.LENGTH_SHORT).show()
            createFallbackCanvas()
        }
    }

    private fun createFallbackCanvas() {
        try {
            val metrics = resources.displayMetrics
            canvasWidth = metrics.widthPixels - 100
            canvasHeight = metrics.heightPixels - 400

            drawBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(drawBitmap)
            drawCanvas.drawColor(Color.WHITE)
            binding.drawingCanvas.setImageBitmap(drawBitmap)
            binding.drawingCanvas.adjustViewBounds = true
            binding.drawingCanvas.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE

            binding.drawingCanvas.setOnTouchListener { _, event ->
                handleTouch(event)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupPaint() {
        paint = Paint().apply {
            color = currentColor
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = brushSize
            isAntiAlias = true
            isDither = true
        }
    }

    private fun updateBrushType() {
        paint.apply {
            color = currentColor
            strokeWidth = brushSize
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            xfermode = null
            maskFilter = null
            shader = null
            pathEffect = null
            this.alpha = 255

            when (currentBrushType) {
                BrushType.PENCIL -> {
                    // Базовая кисть без эффектов
                }
                BrushType.MARKER -> {
                    this.alpha = 180
                    strokeCap = Paint.Cap.SQUARE
                }
                BrushType.PAINT -> {
                    this.alpha = 220
                    strokeWidth = brushSize * 1.2f
                }
                BrushType.CRAYON -> {
                    this.alpha = 240
                    strokeCap = Paint.Cap.SQUARE
                }
                BrushType.SPRAY -> {
                    this.alpha = 150
                    maskFilter = BlurMaskFilter(brushSize / 2, BlurMaskFilter.Blur.NORMAL)
                }
                BrushType.CHARCOAL -> {
                    this.alpha = 230
                    strokeCap = Paint.Cap.BUTT
                }
                BrushType.WATERCOLOR -> {
                    this.alpha = 120
                    maskFilter = BlurMaskFilter(brushSize / 3, BlurMaskFilter.Blur.SOLID)
                }
                BrushType.AIRBRUSH -> {
                    this.alpha = 100
                    strokeWidth = brushSize * 2f
                    maskFilter = BlurMaskFilter(brushSize, BlurMaskFilter.Blur.NORMAL)
                }
                BrushType.OIL_PAINT -> {
                    this.alpha = 240
                    strokeWidth = brushSize * 1.5f
                }
                BrushType.INK -> {
                    // Базовая кисть
                }
                BrushType.PASTEL -> {
                    this.alpha = 200
                    strokeWidth = brushSize * 1.6f
                }
                BrushType.GLOW -> {
                    this.alpha = 80
                    strokeWidth = brushSize * 3f
                    maskFilter = BlurMaskFilter(brushSize, BlurMaskFilter.Blur.NORMAL)
                }
            }

            if (isEraser) {
                color = Color.WHITE
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                this.alpha = 255
                maskFilter = null
                shader = null
                pathEffect = null
            }
        }

        val currentBrushItem = brushItems.find { it.type == currentBrushType }
        currentBrushItem?.let {
            binding.tvCurrentBrushIcon.text = it.emoji
            binding.tvCurrentBrush.text = it.name
            brushPaletteAdapter?.setSelectedBrush(it.type)
        }
    }

    private fun setupPalettes() {
        colorPaletteAdapter = ColorPaletteAdapter(baseColors) { color ->
            currentBaseColor = color
            generateShades(color)
            shadePaletteAdapter.updateShades(shades)
            currentColor = color
            isEraser = false
            updateBrushType()
            binding.currentColorPreview.setBackgroundColor(currentColor)
            updateBrushPreview()
        }

        binding.colorPaletteRecycler.apply {
            layoutManager = GridLayoutManager(this@DrawingActivity, 6)
            adapter = colorPaletteAdapter
        }

        generateShades(currentBaseColor)
        shadePaletteAdapter = ShadePaletteAdapter(shades) { color ->
            currentColor = color
            isEraser = false
            updateBrushType()
            binding.btnEraser.isSelected = false
            binding.currentColorPreview.setBackgroundColor(currentColor)
            updateBrushPreview()
        }

        binding.shadePaletteRecycler.apply {
            layoutManager = GridLayoutManager(this@DrawingActivity, 8)
            adapter = shadePaletteAdapter
        }
    }

    private fun setupBrushPalette() {
        brushPaletteAdapter = BrushPaletteAdapter(brushItems) { brushItem ->
            currentBrushType = brushItem.type
            isEraser = false
            updateBrushType()
            binding.btnEraser.isSelected = false
        }

        binding.brushPaletteRecycler.apply {
            layoutManager = GridLayoutManager(this@DrawingActivity, 4)
            adapter = brushPaletteAdapter
        }

        brushPaletteAdapter?.setSelectedBrush(currentBrushType)
    }

    private fun setupBrushListeners() {
        val quickColors = mapOf(
            binding.colorBlack to Color.BLACK,
            binding.colorWhite to Color.WHITE,
            binding.colorRed to Color.RED,
            binding.colorGreen to Color.GREEN,
            binding.colorBlue to Color.BLUE,
            binding.colorYellow to Color.YELLOW,
            binding.colorPurple to Color.MAGENTA,
            binding.colorOrange to Color.parseColor("#FF9800"),
            binding.colorPink to Color.parseColor("#E91E63"),
            binding.colorCyan to Color.parseColor("#00BCD4")
        )

        quickColors.forEach { (view, color) ->
            view.setOnClickListener {
                currentColor = color
                isEraser = false
                updateBrushType()
                binding.btnEraser.isSelected = false
                binding.currentColorPreview.setBackgroundColor(currentColor)
                updateBrushPreview()
            }
        }
    }

    private fun setupUndoRedoButtons() {
        binding.btnUndo.setOnClickListener {
            undo()
        }

        binding.btnRedo.setOnClickListener {
            redo()
        }
    }

    private fun generateShades(baseColor: Int) {
        shades.clear()

        val hsv = FloatArray(3)
        Color.colorToHSV(baseColor, hsv)
        val hue = hsv[0]

        for (i in 0 until 8) {
            val saturation = 0.2f + 0.8f * (i / 7f)
            val brightness = 0.2f + 0.8f * ((7 - i) / 7f)
            shades.add(Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
        }

        shades.add(Color.BLACK)
        shades.add(Color.parseColor("#333333"))
        shades.add(Color.parseColor("#666666"))
        shades.add(Color.parseColor("#999999"))
        shades.add(Color.parseColor("#CCCCCC"))
        shades.add(Color.WHITE)
    }

    private fun setupClickListeners() {
        // ВОССТАНОВИЛ ЛЕВУЮ ВЕРХНЮЮ КНОПКУ
        binding.btnToggleBrushesMain.setOnClickListener {
            toggleBrushPalette()
        }

        binding.btnTogglePalette.setOnClickListener {
            toggleAdvancedPalette()
        }

        binding.btnToggleBrushes.setOnClickListener {
            toggleBrushPalette()
        }

        binding.seekBarBrush.apply {
            progress = brushSize.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    brushSize = progress.toFloat()
                    updateBrushType()
                    updateBrushPreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    Toast.makeText(
                        this@DrawingActivity,
                        "Размер кисти: ${brushSize.toInt()}px",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }

        binding.btnEraser.setOnClickListener {
            isEraser = !isEraser
            binding.btnEraser.isSelected = isEraser
            if (isEraser) {
                binding.tvCurrentBrush.text = "Ластик"
                binding.tvCurrentBrushIcon.text = "🧽"
            } else {
                val currentBrushItem = brushItems.find { it.type == currentBrushType }
                currentBrushItem?.let {
                    binding.tvCurrentBrush.text = it.name
                    binding.tvCurrentBrushIcon.text = it.emoji
                }
            }
            updateBrushType()
            updateBrushPreview()
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Очистить холст")
                .setMessage("Вы уверены, что хотите очистить весь рисунок?")
                .setPositiveButton("Да") { _, _ ->
                    clearCanvas()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        binding.btnSave.setOnClickListener {
            saveDrawingToGallery()
        }

        binding.btnHideUI.setOnClickListener {
            toggleUI()
        }

        binding.btnColorPicker.setOnClickListener {
            showAdvancedColorPicker()
        }
    }

    private fun updateBrushPreview() {
        try {
            binding.tvBrushSize.text = brushSize.toInt().toString()

            val previewColor = if (isEraser) Color.GRAY else currentColor
            binding.currentColorPreview.setBackgroundColor(previewColor)

            if (binding.brushPreviewCircle != null) {
                val previewSize = brushSize.coerceAtLeast(4f).coerceAtMost(60f)
                val layoutParams = binding.brushPreviewCircle.layoutParams
                layoutParams.width = previewSize.toInt()
                layoutParams.height = previewSize.toInt()
                binding.brushPreviewCircle.layoutParams = layoutParams
                binding.brushPreviewCircle.setBackgroundColor(previewColor)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            binding.tvBrushSize.text = brushSize.toInt().toString()
        }
    }

    private fun toggleAdvancedPalette() {
        isAdvancedPaletteVisible = !isAdvancedPaletteVisible
        binding.paletteContainer.visibility = if (isAdvancedPaletteVisible) View.VISIBLE else View.GONE
        binding.btnTogglePalette.text = if (isAdvancedPaletteVisible) "▲ Свернуть палитру" else "▼ Показать палитру"
    }

    private fun toggleBrushPalette() {
        val isVisible = binding.brushPaletteContainer.visibility == View.VISIBLE
        binding.brushPaletteContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.btnToggleBrushes.text = if (isVisible) "▼ Показать кисти" else "▲ Свернуть кисти"
        binding.btnToggleBrushesMain.text = if (isVisible) "🖌" else "🖌✓"
    }

    private fun toggleUI() {
        if (uiVisible) {
            hideUI()
        } else {
            showUI()
        }
        uiVisible = !uiVisible
    }

    private fun showAdvancedColorPicker() {
        try {
            // Инфлейтим layout
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null)

            // Находим все View
            val sbHue = dialogView.findViewById<SeekBar>(R.id.sbHue)
            val sbSaturation = dialogView.findViewById<SeekBar>(R.id.sbSaturation)
            val sbBrightness = dialogView.findViewById<SeekBar>(R.id.sbBrightness)
            val sbAlpha = dialogView.findViewById<SeekBar>(R.id.sbAlpha)

            val tvHueLabel = dialogView.findViewById<TextView>(R.id.tvHueLabel)
            val tvSaturationLabel = dialogView.findViewById<TextView>(R.id.tvSaturationLabel)
            val tvBrightnessLabel = dialogView.findViewById<TextView>(R.id.tvBrightnessLabel)
            val tvAlphaLabel = dialogView.findViewById<TextView>(R.id.tvAlphaLabel)

            val vColorPreview = dialogView.findViewById<View>(R.id.vColorPreview)
            val tvHexLabel = dialogView.findViewById<TextView>(R.id.tvHexLabel)
            val tvArgbLabel = dialogView.findViewById<TextView>(R.id.tvArgbLabel)
            val tvHsvLabel = dialogView.findViewById<TextView>(R.id.tvHsvLabel)
            val tvAlphaPercentLabel = dialogView.findViewById<TextView>(R.id.tvAlphaPercentLabel)

            val glQuickColors = dialogView.findViewById<GridLayout>(R.id.glQuickColors)
            val btnReset = dialogView.findViewById<Button>(R.id.btnReset)
            val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
            val btnSelect = dialogView.findViewById<Button>(R.id.btnSelect)

            // Устанавливаем начальные значения
            sbHue.progress = hue
            sbSaturation.progress = saturation
            sbBrightness.progress = brightness
            sbAlpha.progress = alpha

            // Обновляем начальный предпросмотр
            updateColorPreviewFromDialog(
                sbHue, sbSaturation, sbBrightness, sbAlpha,
                tvHueLabel, tvSaturationLabel, tvBrightnessLabel, tvAlphaLabel,
                vColorPreview, tvHexLabel, tvArgbLabel, tvHsvLabel, tvAlphaPercentLabel
            )

            // Добавляем быстрые цвета
            val quickColors = listOf(
                Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN,
                Color.BLACK, Color.WHITE, Color.parseColor("#FFA500"), Color.parseColor("#800080"),
                Color.parseColor("#A52A2A"), Color.parseColor("#008000")
            )

            // Очищаем GridLayout перед добавлением
            glQuickColors.removeAllViews()

            for (color in quickColors) {
                val colorView = View(this)
                val params = GridLayout.LayoutParams().apply {
                    width = dpToPx(48)
                    height = dpToPx(48)
                    setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                }
                colorView.layoutParams = params
                colorView.setBackgroundColor(color)
                colorView.setOnClickListener {
                    setColorFromQuickSelection(
                        color, sbHue, sbSaturation, sbBrightness,
                        tvHueLabel, tvSaturationLabel, tvBrightnessLabel, vColorPreview,
                        tvHexLabel, tvArgbLabel, tvHsvLabel, tvAlphaPercentLabel
                    )
                }
                glQuickColors.addView(colorView)
            }

            // Создаем диалог
            val dialog = AlertDialog.Builder(this)
                .setTitle("Выбор цвета")
                .setView(dialogView)
                .setNegativeButton("ОТМЕНА", null)
                .show()

            // Обработчики событий слайдеров
            sbHue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    tvHueLabel.text = "Оттенок (Hue): ${progress}°"
                    updateColorPreviewFromDialog(
                        sbHue, sbSaturation, sbBrightness, sbAlpha,
                        tvHueLabel, tvSaturationLabel, tvBrightnessLabel, tvAlphaLabel,
                        vColorPreview, tvHexLabel, tvArgbLabel, tvHsvLabel, tvAlphaPercentLabel
                    )
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            sbSaturation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    tvSaturationLabel.text = "Насыщенность (Saturation): ${progress}%"
                    updateColorPreviewFromDialog(
                        sbHue, sbSaturation, sbBrightness, sbAlpha,
                        tvHueLabel, tvSaturationLabel, tvBrightnessLabel, tvAlphaLabel,
                        vColorPreview, tvHexLabel, tvArgbLabel, tvHsvLabel, tvAlphaPercentLabel
                    )
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            sbBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    tvBrightnessLabel.text = "Яркость (Brightness): ${progress}%"
                    updateColorPreviewFromDialog(
                        sbHue, sbSaturation, sbBrightness, sbAlpha,
                        tvHueLabel, tvSaturationLabel, tvBrightnessLabel, tvAlphaLabel,
                        vColorPreview, tvHexLabel, tvArgbLabel, tvHsvLabel, tvAlphaPercentLabel
                    )
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            sbAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    tvAlphaLabel.text = "Прозрачность (Alpha): ${progress}%"
                    updateColorPreviewFromDialog(
                        sbHue, sbSaturation, sbBrightness, sbAlpha,
                        tvHueLabel, tvSaturationLabel, tvBrightnessLabel, tvAlphaLabel,
                        vColorPreview, tvHexLabel, tvArgbLabel, tvHsvLabel, tvAlphaPercentLabel
                    )
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Обработчики кнопок
            btnReset.setOnClickListener {
                sbHue.progress = 0
                sbSaturation.progress = 100
                sbBrightness.progress = 100
                sbAlpha.progress = 100
                updateColorPreviewFromDialog(
                    sbHue, sbSaturation, sbBrightness, sbAlpha,
                    tvHueLabel, tvSaturationLabel, tvBrightnessLabel, tvAlphaLabel,
                    vColorPreview, tvHexLabel, tvArgbLabel, tvHsvLabel, tvAlphaPercentLabel
                )
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            btnSelect.setOnClickListener {
                hue = sbHue.progress
                saturation = sbSaturation.progress
                brightness = sbBrightness.progress
                alpha = sbAlpha.progress

                val color = hsvToColor(hue, saturation, brightness, alpha)
                currentColor = color
                isEraser = false
                updateBrushType()
                binding.btnEraser.isSelected = false
                binding.currentColorPreview.setBackgroundColor(currentColor)
                updateBrushPreview()
                dialog.dismiss()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка открытия палитры", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateColorPreviewFromDialog(
        hueSeekBar: SeekBar, saturationSeekBar: SeekBar,
        brightnessSeekBar: SeekBar, alphaSeekBar: SeekBar,
        hueLabel: TextView, saturationLabel: TextView,
        brightnessLabel: TextView, alphaLabel: TextView,
        colorPreviewView: View, hexLabel: TextView,
        argbLabel: TextView, hsvLabel: TextView, alphaPercentLabel: TextView
    ) {
        val h = hueSeekBar.progress
        val s = saturationSeekBar.progress
        val v = brightnessSeekBar.progress
        val a = alphaSeekBar.progress

        val color = hsvToColor(h, s, v, a)

        colorPreviewView.setBackgroundColor(color)
        hexLabel.text = "HEX: #${Integer.toHexString(color).uppercase(Locale.getDefault())}"
        argbLabel.text = "ARGB: ${Color.alpha(color)}, ${Color.red(color)}, ${Color.green(color)}, ${Color.blue(color)}"
        hsvLabel.text = "HSV: ${h}°, ${s}%, ${v}%"
        alphaPercentLabel.text = "Прозрачность: ${a}%"

        alphaLabel.text = "Прозрачность (Alpha): ${a}%"
    }

    private fun setColorFromQuickSelection(
        color: Int, hueSeekBar: SeekBar, saturationSeekBar: SeekBar,
        brightnessSeekBar: SeekBar, hueLabel: TextView,
        saturationLabel: TextView, brightnessLabel: TextView,
        colorPreviewView: View, hexLabel: TextView,
        argbLabel: TextView, hsvLabel: TextView, alphaPercentLabel: TextView
    ) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)

        val hue = (hsv[0] * 360).toInt()
        val saturation = (hsv[1] * 100).toInt()
        val brightness = (hsv[2] * 100).toInt()

        hueSeekBar.progress = hue
        saturationSeekBar.progress = saturation
        brightnessSeekBar.progress = brightness

        hueLabel.text = "Оттенок (Hue): ${hue}°"
        saturationLabel.text = "Насыщенность (Saturation): ${saturation}%"
        brightnessLabel.text = "Яркость (Brightness): ${brightness}%"

        colorPreviewView.setBackgroundColor(color)
        hexLabel.text = "HEX: #${Integer.toHexString(color).uppercase(Locale.getDefault())}"
        argbLabel.text = "ARGB: ${Color.alpha(color)}, ${Color.red(color)}, ${Color.green(color)}, ${Color.blue(color)}"
        hsvLabel.text = "HSV: ${hue}°, ${saturation}%, ${brightness}%"
        alphaPercentLabel.text = "Прозрачность: ${Color.alpha(color) * 100 / 255}%"
    }

    // Вспомогательная функция для конвертации dp в px
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun hsvToColor(hue: Int, saturation: Int, brightness: Int, alphaPercent: Int): Int {
        val hsv = floatArrayOf(hue.toFloat(), saturation.toFloat() / 100, brightness.toFloat() / 100)
        val color = Color.HSVToColor(hsv)
        val alpha = (alphaPercent * 255 / 100)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        try {
            if (!::drawBitmap.isInitialized) {
                return false
            }

            // Корректируем координаты для фиксированного размера холста
            val viewWidth = binding.drawingCanvas.width.toFloat()
            val viewHeight = binding.drawingCanvas.height.toFloat()
            val scaleX = canvasWidth.toFloat() / viewWidth
            val scaleY = canvasHeight.toFloat() / viewHeight

            val x = event.x * scaleX
            val y = event.y * scaleY

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    path = Path()
                    path.moveTo(x, y)
                }
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(x, y)
                    redrawCanvas()
                }
                MotionEvent.ACTION_UP -> {
                    path.lineTo(x, y)

                    val savedPath = Path(path)
                    val paintCopy = Paint(paint)
                    paths.add(DrawingPath(savedPath, paintCopy, currentBrushType, currentColor, brushSize))

                    redrawCanvas()
                    path.reset()
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun redrawCanvas() {
        try {
            if (!::drawBitmap.isInitialized) return

            drawCanvas.drawColor(Color.WHITE)

            for (drawingPath in paths) {
                drawCanvas.drawPath(drawingPath.path, drawingPath.paint)
            }

            if (!path.isEmpty) {
                drawCanvas.drawPath(path, paint)
            }

            binding.drawingCanvas.invalidate()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideUI() {
        // Скрываем только нижние элементы, оставляем верхнюю панель
        binding.toolsContainer.visibility = View.GONE
        binding.colorContainer.visibility = View.GONE
        binding.paletteContainer.visibility = View.GONE
        binding.brushPaletteContainer.visibility = View.GONE

        // Не скрываем верхнюю панель и не меняем размер холста
        binding.btnHideUI.text = "👁✓"
    }

    private fun showUI() {
        // Восстанавливаем видимость нижних элементов
        binding.toolsContainer.visibility = View.VISIBLE
        binding.colorContainer.visibility = View.VISIBLE

        if (isAdvancedPaletteVisible) {
            binding.paletteContainer.visibility = View.VISIBLE
        }
        binding.brushPaletteContainer.visibility = View.VISIBLE
        binding.btnHideUI.text = "👁"
    }

    private fun clearCanvas() {
        paths.clear()
        undonePaths.clear()
        path.reset()
        if (::drawBitmap.isInitialized) {
            drawCanvas.drawColor(Color.WHITE)
            binding.drawingCanvas.invalidate()
        }
    }

    private fun saveDrawingToGallery() {
        if (!::drawBitmap.isInitialized) {
            Toast.makeText(this, "Холст не готов", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Сохранить рисунок")
            .setMessage("Сохранить рисунок в галерею устройства?")
            .setPositiveButton("Сохранить") { _, _ ->
                try {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val fileName = "ARTCHAT_$timeStamp.jpg"

                    MediaStore.Images.Media.insertImage(
                        contentResolver,
                        drawBitmap,
                        fileName,
                        "Рисунок из ArtChat"
                    )

                    Toast.makeText(this, "Рисунок сохранен в галерею!", Toast.LENGTH_LONG).show()

                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun undo() {
        if (paths.isNotEmpty()) {
            undonePaths.add(paths.removeAt(paths.size - 1))
            redrawCanvas()
            Toast.makeText(this, "Отменено", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Нечего отменять", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redo() {
        if (undonePaths.isNotEmpty()) {
            paths.add(undonePaths.removeAt(undonePaths.size - 1))
            redrawCanvas()
            Toast.makeText(this, "Повторено", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Нечего повторять", Toast.LENGTH_SHORT).show()
        }
    }

    inner class ColorPaletteAdapter(
        private val colors: List<Int>,
        private val onColorClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ColorPaletteAdapter.ViewHolder>() {

        inner class ViewHolder(private val binding: ColorItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(color: Int) {
                binding.colorView.setBackgroundColor(color)
                binding.root.setOnClickListener {
                    onColorClick(color)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ColorItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(colors[position])
        }

        override fun getItemCount(): Int = colors.size
    }

    inner class ShadePaletteAdapter(
        private var shades: MutableList<Int>,
        private val onShadeClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ShadePaletteAdapter.ViewHolder>() {

        inner class ViewHolder(private val binding: ColorItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(color: Int) {
                binding.colorView.setBackgroundColor(color)
                binding.root.setOnClickListener {
                    onShadeClick(color)
                }
            }
        }

        fun updateShades(newShades: MutableList<Int>) {
            shades = newShades
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ColorItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(shades[position])
        }

        override fun getItemCount(): Int = shades.size
    }

    inner class BrushPaletteAdapter(
        private val brushItems: List<BrushItem>,
        private val onBrushClick: (BrushItem) -> Unit
    ) : RecyclerView.Adapter<BrushPaletteAdapter.ViewHolder>() {

        private var selectedPosition = 0

        inner class ViewHolder(private val binding: BrushItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(brushItem: BrushItem, position: Int) {
                binding.brushIcon.text = brushItem.emoji
                binding.brushName.text = brushItem.name
                binding.root.isSelected = position == selectedPosition

                binding.root.setOnClickListener {
                    val previousPosition = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(previousPosition)
                    notifyItemChanged(position)

                    onBrushClick(brushItem)
                }
            }
        }

        fun setSelectedBrush(brushType: BrushType) {
            val newPosition = brushItems.indexOfFirst { it.type == brushType }
            if (newPosition != -1 && newPosition != selectedPosition) {
                val previousPosition = selectedPosition
                selectedPosition = newPosition
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = BrushItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(brushItems[position], position)
        }

        override fun getItemCount(): Int = brushItems.size
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::drawBitmap.isInitialized && !drawBitmap.isRecycled) {
            drawBitmap.recycle()
        }
    }
}