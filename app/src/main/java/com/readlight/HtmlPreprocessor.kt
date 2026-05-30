package com.readlight

import io.documentnode.epub4j.domain.Book

object HtmlPreprocessor {

    fun preprocessHtml(rawHtml: String, book: Book): Pair<String, String> {
        var html = rawHtml

        if (html.contains("calibre:cover", ignoreCase = true)) {
            val href = Regex(
                """xlink:href=["']([^"']+)["']|(?<![x]link:)href=["']([^"']+\.(jpe?g|png|gif|webp))["']""",
                RegexOption.IGNORE_CASE
            ).find(html)
            val imagePath = href?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
            if (imagePath != null) {
                val filename = imagePath.substringAfterLast("/")
                return Pair(
                    """<img src="https://epub.local/$filename" role="doc-cover"/>""",
                    ""
                )
            }
        }

        val alignmentCss = extractEpubStructuralCss(html, book)

        html = Regex("""<body\b[^>]*>([\s\S]*?)</body>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: html

            html = html.replace(
                Regex("""<div\b[^>]*id\s*=\s*["']calibre_pb_\d+["'][^>]*>\s*</div>""",
                    RegexOption.IGNORE_CASE),
                ""
            )

            html = html.trimEnd()
            html = html.replace(
                Regex("""(<br\b[^>]*class[^>]*/?>\s*)+$""", RegexOption.IGNORE_CASE),
                ""
            )

        html = html.replace(
            Regex("""<p(\b[^>]*)>\s*<br\b[^>]*class=[^>]*/?>.*?</p>""", RegexOption.IGNORE_CASE),
            "<p$1>&nbsp;</p>"
        )

        html = Regex("""<p\b[^>]*>[\s\S]*?</p>""", RegexOption.IGNORE_CASE).replace(html) { pMatch ->
            pMatch.value.replace(
                Regex("""<br\b[^>]*class=[^>]*/?>""", RegexOption.IGNORE_CASE), ""
            )
        }

        html = html.replace(
            Regex("""(<img\b[^>]*/?>)\s*(<br\s*/?>)+""", RegexOption.IGNORE_CASE),
            "$1"
        )
        html = html.replace(
            Regex("""<p(\b[^>]*)>(\s|<br\s*/?>)*</p>""", RegexOption.IGNORE_CASE),
            "<p$1></p>"
        )
        html = html.replace(
            Regex("""<style\b[^>]*>[\s\S]*?</style>""", RegexOption.IGNORE_CASE), ""
        )
        html = html.replace(
            Regex("""<link\b[^>]*rel=["']?stylesheet["']?[^>]*>""", RegexOption.IGNORE_CASE), ""
        )
        html = html.replace(Regex("""src=["']([^"']+)["']""")) { match ->
            val filename = match.groupValues[1].substringAfterLast("/")
            """src="https://epub.local/$filename""""
        }

        return Pair(html, alignmentCss)
    }


    private fun extractEpubStructuralCss(html: String, book: Book): String {
        val allCss = StringBuilder()

        Regex("""<style\b[^>]*>([\s\S]*?)</style>""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { allCss.append(it.groupValues[1]).append("\n") }

        Regex("""<link\b[^>]*href=["']([^"'#?]+\.css)["']""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { match ->
                val href = match.groupValues[1]
                val filename = href.substringAfterLast("/")
                book.resources.all
                    .filter { res ->
                        val resFile = res.href?.substringAfterLast("/") ?: ""
                        resFile == filename || res.href == href
                    }
                    .forEach { res ->
                        try { allCss.append(String(res.data)).append("\n") } catch (_: Exception) {}
                    }
            }

        if (allCss.isBlank()) return ""

        val result = StringBuilder()
        Regex("""([^{}]+)\{([^}]+)\}""", RegexOption.IGNORE_CASE)
            .findAll(allCss)
            .forEach { match ->
                val declarations = match.groupValues[2]
                val selectors = match.groupValues[1]
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                selectors.forEach { selector ->
                    if (!selector.contains('.') && !selector.contains('#')) return@forEach
                    if (selector.contains('@')) return@forEach

                    val alignMatch = Regex(
                        """text-align\s*:\s*(center|right)""", RegexOption.IGNORE_CASE
                    ).find(declarations)

                    if (alignMatch != null) {
                        val alignment = alignMatch.groupValues[1].lowercase()
                        result.append("$selector { text-align: $alignment !important; }\n")
                        if (alignment == "center") {
                            result.append("$selector { text-indent: 0 !important; }\n")
                            result.append("$selector { margin-top: 1.7em !important; margin-bottom: 1.7em !important; }\n")
                            result.append("$selector:first-child { margin-top: 0 !important; }\n")
                        }
                    }

                    Regex("""font-weight\s*:\s*(\w+)""", RegexOption.IGNORE_CASE)
                        .find(declarations)?.let {
                            result.append("$selector { font-weight: ${it.groupValues[1]} !important; }\n")
                        }
                    Regex("""font-style\s*:\s*(\w+)""", RegexOption.IGNORE_CASE)
                        .find(declarations)?.let {
                            result.append("$selector { font-style: ${it.groupValues[1]} !important; }\n")
                        }
                }
            }
        return result.toString()
    }

    fun stripHtmlToText(html: String): String = html
        .replace(Regex("""<[^>]+>"""), " ")
        .replace("&amp;",  "&")
        .replace("&lt;",   "<")
        .replace("&gt;",   ">")
        .replace("&nbsp;", " ")
        .replace(Regex("""\s+"""), " ")
        .replace(Regex("""^\s*\[[^\]]*]\s*"""), "")
        .trim()

    fun findFootnoteContent(
        fragment: String,
        file: String,
        book: Book,
        currentChapterRawHtml: String
    ): String {
        val searchHtml = if (file.isBlank()) {
            currentChapterRawHtml
        } else {
            val filename = file.substringAfterLast("/")
            book.contents.find { res ->
                val resFile = res.href?.substringAfterLast("/") ?: ""
                resFile == filename || res.href == file
            }?.let {
                try { String(it.data) } catch (e: Exception) { currentChapterRawHtml }
            } ?: currentChapterRawHtml
        }

        val escaped = Regex.escape(fragment)

        val direct = Regex(
            """<(p|div|section|aside|li)\b[^>]+id\s*=\s*["']$escaped["'][^>]*>[\s\S]*?</\1>""",
            RegexOption.IGNORE_CASE
        ).find(searchHtml)?.value

        if (direct != null) return stripHtmlToText(direct)

        val idMatch = Regex("""id\s*=\s*["']$escaped["']""", RegexOption.IGNORE_CASE)
            .find(searchHtml) ?: return ""
        val idPos = idMatch.range.first

        val blockTagRegex = Regex("""<(p|div|section|aside|li)\b[^>]*>""", RegexOption.IGNORE_CASE)
        val lastBlock = blockTagRegex.findAll(searchHtml.substring(0, idPos)).lastOrNull()
            ?: return ""

        val tagName    = lastBlock.groupValues[1].lowercase()
        val blockStart = lastBlock.range.first

        val closeTag = "</$tagName>"
        val closePos = searchHtml.indexOf(closeTag, idPos, ignoreCase = true)
        if (closePos < 0) return ""

        val raw = searchHtml.substring(blockStart, closePos + closeTag.length)
        return stripHtmlToText(raw)
    }
}
