package ru.sberdevices.sbdv.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable

class AvatarPlaceholder(
    val textColor: Int = Color.WHITE,
    val textTypeFace: Typeface = Typeface.SANS_SERIF
) : Drawable() {

    private val backgroundPaint by lazy {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
    }

    private val textPaint by lazy {
        Paint().apply {
            isAntiAlias = true
            color = textColor
            typeface = textTypeFace
        }
    }

    private val placeholderBounds = RectF(0F, 0F, bounds.width().toFloat(), bounds.height().toFloat())

    private var avatarText: String = ""

    fun setName(name: String?, surname: String? = null) {
        avatarText = determinePlaceholderText(name = name, surname = surname)
    }

    override fun draw(canvas: Canvas) {
        placeholderBounds.right = bounds.width().toFloat()
        placeholderBounds.bottom = bounds.height().toFloat()
        backgroundPaint.color = BACKGROUND_COLORS[avatarText.hashCode() % BACKGROUND_COLORS.size]
        canvas.drawOval(placeholderBounds, backgroundPaint)

        textPaint.textSize = calculateTextSize()
        val textStartXPoint = calculateTextStartXPoint()
        val textStartYPoint = calculateTextStartYPoint()
        canvas.drawText(avatarText, textStartXPoint, textStartYPoint, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        textPaint.alpha = alpha
        backgroundPaint.alpha = alpha
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(p0: ColorFilter?) {
        textPaint.colorFilter = colorFilter
        backgroundPaint.colorFilter = colorFilter
    }

    private fun calculateTextStartXPoint(): Float {
        val stringWidth = textPaint.measureText(avatarText)
        return bounds.width() / 2f - stringWidth / 2f
    }

    private fun calculateTextStartYPoint(): Float {
        return bounds.height() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
    }

    private fun calculateTextSize(): Float {
        return bounds.height() * DEFAULT_TEXT_SIZE_PERCENTAGE.toFloat() / 100
    }

    private companion object {
        private const val DEFAULT_TEXT_SIZE_PERCENTAGE = 33

        private val BACKGROUND_COLORS = listOf(
            -0xd32f2f, -0xC2185B, -0x7B1FA2, -0x512DA8,
            -0x303F9F, -0x1976D2, -0x0288D1, -0x0097A7,
            -0x00796B, -0x388E3C, -0x689F38, -0xAFB42B,
            -0xFBC02D, -0xFFA000, -0xF57C00, -0xE64A19,
            -0x5D4037, -0x616161, -0x455A64
        )

        private val EMOJI_REGEXP = "[^\\p{L}\\p{N}\\p{P}\\p{Z}]".toRegex()
        private val SIGN_REGEXP = "[`~!@#$%^&*()_+={}\\[\\]|:;“’<,>.?]".toRegex()

        @JvmStatic
        fun determinePlaceholderText(name: String?, surname: String? = null): String {
            return StringBuilder().apply {
                if (!name.isNullOrBlank()) append(getUppercaseSignOrEmoji(name))
                if (!surname.isNullOrBlank()) append(getUppercaseSignOrEmoji(surname))
                if (isEmpty()) append("-")
            }.toString()
        }

        /**
         * Возвращает первый подходящий символ(цифра или заглавная буква) или emoji, либо пустую строку если их нет
         */
        private fun getUppercaseSignOrEmoji(word: String): String {
            /**
             * [EMOJI_REGEXP] шире чем только эмодзи (спец символы тоже включает, и, например, $😀 - валидный результат поиска)
             * Поэтому перед поиском emoji убираем специальные символы
             */
            val emoji = EMOJI_REGEXP.find(word.replace(SIGN_REGEXP, ""))

            val sign = try {
                word.first { it.isLetterOrDigit() }
            } catch (e: NoSuchElementException) {
                null
            }

            return when {
                sign != null && emoji != null -> {
                    val emojiRange = emoji.range
                    val emojiValue = emoji.value

                    val signPosition = word.indexOf(sign)

                    if (signPosition < emojiRange.first) {
                        sign.toString().uppercase()
                    } else {
                        emojiValue
                    }
                }
                sign != null -> {
                    sign.toString().uppercase()
                }
                emoji != null -> {
                    emoji.value
                }
                else -> {
                    ""
                }
            }
        }
    }
}
