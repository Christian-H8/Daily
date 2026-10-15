package com.christianh.daily.domain

/**
 * The small subset of markdown the corpus actually uses, resolved into text plus
 * spans. Settles the "Markdown rendering" item [[DECISIONS]] carried as open.
 *
 * **Render rather than flatten.** The open item offered two answers: render a
 * subset on the device, or strip the markers at publish time. Stripping loses
 * information the entries depend on — a `principle` opens its counterweight with a
 * bolded lead, and `*Nicomachean Ethics*` is a book rather than a shout. The whole
 * corpus is authored in markdown by hand, so the markers are going to be there; the
 * only question was whether the device honoured them or showed them.
 *
 * **Why the parsing is here and the styling is not.** Emphasis is decided entirely
 * by where the asterisks are, which is a string problem and testable at a desk.
 * Turning a span into a weight needs `androidx.compose.ui.text`, which `domain` may
 * not import. So this returns [Span]s as plain data and the view turns them into
 * an `AnnotatedString` — the same split as [FlickDetector], for the same reason.
 *
 * Handles `*italic*`, `**bold**`, `***both***`, and nesting. Deliberately nothing
 * else: no links, no code, no headings. Headings are already sections and the rest
 * has never appeared in an entry.
 */
object Inline {

    enum class Emphasis { ITALIC, BOLD, BOLD_ITALIC }

    /** Half-open range over [Marked.text], never over the source. */
    data class Span(val start: Int, val end: Int, val emphasis: Emphasis)

    data class Marked(val text: String, val spans: List<Span>)

    fun parse(source: String): Marked {
        val out = StringBuilder()
        val spans = mutableListOf<Span>()
        scan(source, out, spans)
        return Marked(out.toString(), spans)
    }

    /**
     * Recursive so nesting falls out for free: the inner text is scanned by the
     * same function, which records its own spans against the same output, and the
     * outer span is closed afterwards over whatever length that produced.
     */
    private fun scan(source: String, out: StringBuilder, spans: MutableList<Span>) {
        var i = 0
        while (i < source.length) {
            if (source[i] == '*') {
                val marker = when {
                    source.startsWith("***", i) -> "***"
                    source.startsWith("**", i) -> "**"
                    else -> "*"
                }
                val inner = i + marker.length
                val close = source.indexOf(marker, inner)

                // Both space checks matter, and they are what keeps ordinary prose
                // safe. Without them "a * b * c" reads as an emphasis run and a
                // stray asterisk swallows half a paragraph into italics.
                if (close > inner && source[inner] != ' ' && source[close - 1] != ' ') {
                    val start = out.length
                    scan(source.substring(inner, close), out, spans)
                    spans += Span(
                        start = start,
                        end = out.length,
                        emphasis = when (marker) {
                            "***" -> Emphasis.BOLD_ITALIC
                            "**" -> Emphasis.BOLD
                            else -> Emphasis.ITALIC
                        }
                    )
                    i = close + marker.length
                    continue
                }
            }
            out.append(source[i])
            i++
        }
    }
}
