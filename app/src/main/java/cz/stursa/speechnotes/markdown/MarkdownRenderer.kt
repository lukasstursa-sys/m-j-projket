package cz.stursa.speechnotes.markdown

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.text.style.TypefaceSpan

/**
 * Lightweight Markdown renderer that converts markdown text to Android Spannable.
 * Supports: **bold**, *italic*, # headings, - bullet lists, `code`, ~~strikethrough~~
 */
object MarkdownRenderer {

    fun render(markdown: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val lines = markdown.split("\n")

        for ((index, line) in lines.withIndex()) {
            when {
                line.startsWith("### ") -> {
                    val text = line.removePrefix("### ")
                    val start = builder.length
                    builder.append(renderInline(text))
                    builder.setSpan(
                        RelativeSizeSpan(1.1f), start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD), start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                line.startsWith("## ") -> {
                    val text = line.removePrefix("## ")
                    val start = builder.length
                    builder.append(renderInline(text))
                    builder.setSpan(
                        RelativeSizeSpan(1.2f), start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD), start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                line.startsWith("# ") -> {
                    val text = line.removePrefix("# ")
                    val start = builder.length
                    builder.append(renderInline(text))
                    builder.setSpan(
                        RelativeSizeSpan(1.4f), start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD), start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val text = line.trimStart().removePrefix("- ").removePrefix("* ")
                    val start = builder.length
                    builder.append(renderInline(text))
                    builder.setSpan(
                        BulletSpan(20), start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                line.trimStart().startsWith("• ") -> {
                    val text = line.trimStart().removePrefix("• ")
                    val start = builder.length
                    builder.append(renderInline(text))
                    builder.setSpan(
                        BulletSpan(20), start, builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                else -> {
                    builder.append(renderInline(line))
                }
            }

            if (index < lines.size - 1) {
                builder.append("\n")
            }
        }

        return builder
    }

    private fun renderInline(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        var i = 0

        while (i < text.length) {
            when {
                // Bold **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        val start = builder.length
                        builder.append(text.substring(i + 2, end))
                        builder.setSpan(
                            StyleSpan(Typeface.BOLD), start, builder.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        i = end + 2
                    } else {
                        builder.append(text[i])
                        i++
                    }
                }
                // Strikethrough ~~text~~
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end != -1) {
                        val start = builder.length
                        builder.append(text.substring(i + 2, end))
                        builder.setSpan(
                            StrikethroughSpan(), start, builder.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        i = end + 2
                    } else {
                        builder.append(text[i])
                        i++
                    }
                }
                // Italic *text*
                text[i] == '*' && (i + 1 < text.length && text[i + 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1) {
                        val start = builder.length
                        builder.append(text.substring(i + 1, end))
                        builder.setSpan(
                            StyleSpan(Typeface.ITALIC), start, builder.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        i = end + 1
                    } else {
                        builder.append(text[i])
                        i++
                    }
                }
                // Inline code `text`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        val start = builder.length
                        builder.append(text.substring(i + 1, end))
                        builder.setSpan(
                            TypefaceSpan("monospace"), start, builder.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        i = end + 1
                    } else {
                        builder.append(text[i])
                        i++
                    }
                }
                else -> {
                    builder.append(text[i])
                    i++
                }
            }
        }

        return builder
    }
}
