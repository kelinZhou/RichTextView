package com.kelin.richtextview

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.support.annotation.RawRes
import android.support.v7.widget.AppCompatTextView
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * **描述:** 富文本TextView.
 *
 * **创建人:** kelin
 *
 * **创建时间:** 2019-11-25  13:44
 *
 * **版本:** v 1.0.0
 */
class RichTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatTextView(context, attrs, defStyleAttr), RichTextHelper.ClickableTextClickListener {

    //这里不能用by lazy的方式，如果用了就空指针。
    private var textHelper: RichTextHelper? = null
        get() = if (field == null) {
            field = RichTextHelper(this)
            field
        } else {
            field
        }
    private var richSeeMoreText: CharSequence? = null
    private var seeMoreTextColor: String? = null
    private var seeMoreTextStyle: String = ""
    private var seeMoreTextSize: Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14F, context.resources.displayMetrics).toInt()
    private var seeMoreTextPadding: Int = 0
    private var fullText: CharSequence? = null
    private val realWidth: Int
        get() = measuredWidth - paddingLeft - paddingRight

    private val lastLineNumber: Int
        get() = maxLines - 1

    init {
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.RichTextView)
            if (ta != null) {
                val sourceId = ta.getResourceId(R.styleable.RichTextView_textRawResId, View.NO_ID)
                if (sourceId != View.NO_ID) {
                    fullText = textHelper!!.fromText(getStringFromStream(context.resources.openRawResource(sourceId)))
                    text = fullText
                } else {
                    fullText = if (text.isNullOrEmpty()) "" else textHelper!!.fromText(text.toString())
                }
                val st = ta.getString(R.styleable.RichTextView_seeMoreText)
                seeMoreTextColor = ta.getString(R.styleable.RichTextView_seeMoreTextColor)
                seeMoreTextSize = ta.getDimensionPixelSize(R.styleable.RichTextView_seeMoreTextSize, seeMoreTextSize)
                seeMoreTextStyle = mapperStyleByFlag(ta.getInt(R.styleable.RichTextView_seeMoreTextStyle, 0))
                seeMoreTextPadding = ta.getDimensionPixelSize(R.styleable.RichTextView_seeMoreTextPadding, 0)
                if (!st.isNullOrEmpty()) {
                    val clickViewSeeMore = ta.getBoolean(R.styleable.RichTextView_clickViewSeeMore, false)
                    val seeMoreText = fixedSeeMoreText(
                        st,
                        seeMoreTextColor,
                        seeMoreTextSize,
                        seeMoreTextStyle,
                        !clickViewSeeMore
                    )
                    if (clickViewSeeMore) {
                        setOnClickListener { onTextClick(RICH_TAG_SEE_MORE) }
                    } else {
                        movementMethod = LinkMovementMethod()
                        highlightColor = Color.TRANSPARENT
                    }
                    richSeeMoreText = textHelper!!.fromText(seeMoreText)
                }
                ta.recycle()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (richSeeMoreText?.isNotEmpty() == true && maxLines < lineCount && !fullText.isNullOrEmpty()) {
            handTextMeasure()
        }
    }

    private fun getStringFromStream(inputStream: InputStream): String {
        val inputStreamReader = InputStreamReader(inputStream, "UTF-8")
        val reader = BufferedReader(inputStreamReader)
        val sb = StringBuffer("")
        var line = reader.readLine()
        while (line != null) {
            sb.append(line)
            sb.append("<br/>")
            line = reader.readLine()
        }
        return sb.toString()
    }

    fun setTextResource(@RawRes resourceId: Int) {
        fullText = textHelper!!.fromText(getStringFromStream(context.resources.openRawResource(resourceId)))
        text = fullText
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        if (text != null && text is String && text.contains("</")) {
            fullText = textHelper!!.fromText(text)
            super.setText(fullText, type)
        } else {
            super.setText(text, type)
        }
    }

    private fun createLayout(source: CharSequence): Layout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder
                .obtain(source, 0, source.length, paint, realWidth)
                .build()
        } else {
            StaticLayout(source, paint, realWidth, Layout.Alignment.ALIGN_NORMAL, 1F, 0F, false)
        }
    }


    private fun fixedSeeMoreText(st: String, stColor: String?, stSize: Int, stStyle: String, needClick: Boolean): String {
        return "${RichTextHelper.TAG_RICH_PREFIX} ${if (stColor.isNullOrEmpty()) "" else "color=$stColor"} size=${stSize}px ${if (stStyle.isEmpty()) "" else stStyle} ${if (needClick) "clickable=$RICH_TAG_SEE_MORE" else ""}>$st${RichTextHelper.TAG_RICH_END}"
    }

    private fun mapperStyleByFlag(styleFlag: Int): String {
        return when (styleFlag) {
            STYLE_BOLD -> "style=b"
            STYLE_ITALIC -> "style=i"
            STYLE_BOLD_ITALIC -> "style=b_i"
            STYLE_BOLD_UNDERLINE -> "style=b_u"
            STYLE_ITALIC_UNDERLINE -> "style=i_u"
            STYLE_ITALIC_UNDERLINE_BOLD -> "style=b_i_u"
            else -> ""
        }
    }

    override fun onTextClick(flag: String) {
        maxLines = Int.MAX_VALUE
        text = fullText
    }

    private fun handTextMeasure() {
        val st = richSeeMoreText
        richSeeMoreText = null
        if (!st.isNullOrEmpty()) {
            val sw = ((createLayout(st).getLineWidth(0) + seeMoreTextPadding + paint.measureText(ELLIPSIS)) * 1.1).toInt()
            if (realWidth >= sw) {
                val lastLine = getLastLineText(lastLineNumber)
                if (lastLine != null) {
                    text = when {
                        lastLine.lastLineText.isEmpty() -> SpannableStringBuilder(text.subSequence(layout.getLineStart(0), layout.getLineEnd(lastLineNumber - 1)))
                            .append(st)
                        lastLine.lastLineNumber == lastLineNumber -> when {
                            layout.getLineWidth(lastLineNumber) + sw < realWidth -> SpannableStringBuilder(text.subSequence(layout.getLineStart(0), layout.getLineEnd(lastLineNumber - 1)))
                                .append(lastLine.lastLineText)
                                .append(ELLIPSIS)
                                .append(st)
                            lastLine.lastLineText.length > 3 -> SpannableStringBuilder(text.subSequence(layout.getLineStart(0), layout.getLineEnd(lastLineNumber - 1)))
                                .append(getShowingText(lastLine.lastLineText.subSequence(0, lastLine.lastLineText.length - 3), sw))
                                .append(ELLIPSIS)
                                .append(st)
                            else -> SpannableStringBuilder(text.subSequence(layout.getLineStart(0), layout.getLineEnd(lastLineNumber - 1)))
                                .append(st)
                        }
                        else -> SpannableStringBuilder(text.subSequence(layout.getLineStart(0), layout.getLineEnd(lastLine.lastLineNumber - 1)))
                            .append(getShowingText(lastLine.lastLineText.subSequence(0, lastLine.lastLineText.length - 3), sw))
                            .append(ELLIPSIS)
                            .append(st)
                    }
                }
            } else {
                throw RuntimeException("The view must be wider than showMoreText.")
            }
        }
    }

    private fun getLastLineText(lineNumber: Int): LastLineInfo? {
        val t = text.subSequence(layout.getLineStart(lineNumber), layout.getLineEnd(lineNumber))
        return if (t.length == 1 && t.contains("\n")) {
            if (lineNumber > 0) {
                getLastLineText(lineNumber - 1)
            } else {
                null
            }
        } else {
            LastLineInfo(
                if (t.endsWith("\n")) {
                    t.subSequence(0, t.length - 1)
                } else {
                    t
                }, lineNumber
            )
        }
    }

    private fun getShowingText(text: CharSequence, showMoreTextWidth: Int): CharSequence {
        return if (paint.measureText(text, 0, text.length) + showMoreTextWidth <= realWidth) {
            text
        } else {
            getShowingText(text.subSequence(0, text.length - 1), showMoreTextWidth)
        }
    }

    companion object {
        private const val RICH_TAG_SEE_MORE = "rich_tag_see_more"
        private const val ELLIPSIS = "... "
        private const val STYLE_BOLD = 0x10
        private const val STYLE_ITALIC = 0x11
        private const val STYLE_BOLD_ITALIC = 0x12
        private const val STYLE_BOLD_UNDERLINE = 0x13
        private const val STYLE_ITALIC_UNDERLINE = 0x14
        private const val STYLE_ITALIC_UNDERLINE_BOLD = 0x15
    }

    private inner class LastLineInfo(val lastLineText: CharSequence, val lastLineNumber: Int)
}