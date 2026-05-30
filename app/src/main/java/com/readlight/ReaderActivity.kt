package com.readlight

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.domain.Resource
import io.documentnode.epub4j.domain.TOCReference
import io.documentnode.epub4j.epub.EpubReader
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: View
    private lateinit var progressBarContainer: View
    private lateinit var tocContainer: View
    private lateinit var tocList: RecyclerView
    private lateinit var book: Book
    private var isBookLoaded = false
    private var currentChapter = 0
    private var currentPage = 0
    private var totalPages = 1
    private var bookPath: String = ""
    private val resourceMap = mutableMapOf<String, Int>()
    private val imageResources = mutableMapOf<String, Resource>()
    private var currentChapterRawHtml = ""
    private var isFootnoteVisible = false
    private var isImageOverlayVisible = false
    private var hideTocAfterLoad = false

    private var isCameraFocusPressed = false
    private var scrollTickCount = 0
    private var isScrolling = false
    private val scrollTickThreshold = 2
    private val pagingCooldownMs = 300L
    private val scrollResetMs = 500L
    private val scrollEndHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scrollEndRunnable = Runnable {
        isScrolling = false
        scrollTickCount = 0
    }

    private lateinit var tocAdapter: TocAdapter
    private val tocChapters = mutableListOf<TocItem>()
    private lateinit var tocScrollbarHelper: ScrollbarHelper
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private companion object {
        const val GO_TO_LAST_PAGE = Int.MAX_VALUE
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        progressBarContainer = findViewById(R.id.progressBarContainer)
        tocContainer = findViewById(R.id.tocContainer)
        tocList = findViewById(R.id.tocList)

        onBackPressedDispatcher.addCallback(this) {
            if (tocContainer.visibility == View.VISIBLE) hideToc()
            else finish()
        }

        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(WebAppInterface(), "Android")
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isLongClickable = false
        webView.isHapticFeedbackEnabled = false
        webView.setOnLongClickListener { true }
        webView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) v.isHapticFeedbackEnabled = false
            false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript(
                    "requestAnimationFrame(function(){" +
                        "requestAnimationFrame(function(){" +
                            "Android.onPageCountReady(getPageCount());" +
                        "});" +
                    "});",
                    null
                )
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleLink(url)
            }

            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                val filename = url.substringAfterLast("/")
                imageResources[filename]?.let { resource ->
                    return WebResourceResponse(
                        resource.mediaType?.toString() ?: "image/png",
                        "UTF-8",
                        ByteArrayInputStream(resource.data)
                    )
                }
                return null
            }
        }

        bookPath = intent.getStringExtra("book_path") ?: ""
        if (bookPath.isEmpty()) {
            Toast.makeText(this, R.string.error_loading_book, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            book = EpubReader().readEpub(FileInputStream(bookPath))

            book.contents.forEachIndexed { index, resource ->
                resource.href?.let { href ->
                    val hrefNoFrag     = href.substringBefore("#")
                    val filename       = href.substringAfterLast("/")
                    val filenameNoFrag = filename.substringBefore("#")

                    resourceMap[href]       = index
                    resourceMap[hrefNoFrag] = index

                    if (!resourceMap.containsKey(filename))       resourceMap[filename]       = index
                    if (!resourceMap.containsKey(filenameNoFrag)) resourceMap[filenameNoFrag] = index
                }
            }

            book.resources.all.forEach { resource ->
                resource.href?.let { href ->
                    if (resource.mediaType?.toString()?.startsWith("image") == true) {
                        imageResources[href] = resource
                        imageResources[href.substringAfterLast("/")] = resource
                    }
                }
            }

            setupTocList()
            val savedChapter = getProgress()
            currentPage = getSavedPage()
            loadChapter(savedChapter, currentPage)

            isBookLoaded = true
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        val keyCode = event?.keyCode ?: return super.dispatchKeyEvent(event)

        if (!isBookLoaded) return super.dispatchKeyEvent(event)

        if (isFootnoteVisible || isImageOverlayVisible) return true

        if (keyCode == LightPhoneKeys.SCROLL_UP || keyCode == LightPhoneKeys.SCROLL_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (tocContainer.visibility == View.VISIBLE) {
                    BrightnessHelper.handleScrollKey(this, keyCode)
                } else {
                    scrollTickCount++
                    scrollEndHandler.removeCallbacks(scrollEndRunnable)
                    scrollEndHandler.postDelayed(scrollEndRunnable, scrollResetMs)

                    if (scrollTickCount >= scrollTickThreshold && !isScrolling) {
                        isScrolling = true
                        if (keyCode == LightPhoneKeys.SCROLL_UP) goToNextPage()
                        else goToPrevPage()
                        scrollTickCount = 0
                        scrollEndHandler.postDelayed({ isScrolling = false }, pagingCooldownMs)
                    }
                }
            }
            return true
        }

        if (keyCode == LightPhoneKeys.CAMERA_FOCUS) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                isCameraFocusPressed = true
            } else if (event.action == KeyEvent.ACTION_UP) {
                if (isCameraFocusPressed) {
                    if (tocContainer.visibility == View.VISIBLE) hideToc()
                    else showToc()
                }
                isCameraFocusPressed = false
            }
            return true
        }

        if (keyCode == LightPhoneKeys.CAMERA_SHUTTER) {
            if (event.action == KeyEvent.ACTION_DOWN && isCameraFocusPressed) {
                finish()
            } else if (event.action == KeyEvent.ACTION_UP) {
                isCameraFocusPressed = false
            }
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (isFootnoteVisible || isImageOverlayVisible) return true
        return super.onGenericMotionEvent(event)
    }

    private fun goToPrevPage() {
        if (currentPage > 0) {
            currentPage--
            webView.evaluateJavascript("goToPage($currentPage)", null)
            updateProgressBar()
            saveProgress()
        } else if (currentChapter > 0) {
            loadChapter(currentChapter - 1, GO_TO_LAST_PAGE)
        }
    }

    private fun goToNextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++
            webView.evaluateJavascript("goToPage($currentPage)", null)
            updateProgressBar()
            saveProgress()
        } else if (currentChapter < book.contents.size - 1) {
            currentPage = 0
            loadChapter(currentChapter + 1, 0)
        }
    }

    private fun updateProgressBar() {
        if (totalPages <= 0) return
        val chapterProgress = (currentPage + 1).toFloat() / totalPages
        val parent = progressBar.parent as View
        parent.post {
            val params = progressBar.layoutParams
            params.height = (parent.height * chapterProgress).toInt()
            progressBar.layoutParams = params
        }
    }

    private fun resolveChapterIndex(href: String): Int? {
        val bare = href.substringBefore("#")
        val filename = bare.substringAfterLast("/")
        return resourceMap[filename]
            ?: resourceMap[bare]
            ?: resourceMap[href]
    }

    private fun collectTocItems(refs: List<TOCReference>, depth: Int) {
        refs.forEach { tocRef ->
            val title = tocRef.title ?: "Chapter"
            val href = tocRef.resource?.href ?: ""
            val index = resolveChapterIndex(href)
            if (index != null) tocChapters.add(TocItem(title, index, depth))
            tocRef.children?.let { if (it.isNotEmpty()) collectTocItems(it, depth + 1) }
        }
    }

    private fun setupTocList() {
        tocList.layoutManager = LinearLayoutManager(this)
        book.tableOfContents?.tocReferences?.let { collectTocItems(it, 0) }
        if (tocChapters.isEmpty()) {
            book.contents.forEachIndexed { index, resource ->
                tocChapters.add(TocItem(resource.title ?: "Chapter ${index + 1}", index, 0))
            }
        }

        tocAdapter = TocAdapter(tocChapters, { currentChapter }) { index ->
            currentPage = 0
            hideTocAfterLoad = true
            loadChapter(index, 0)
        }
        tocList.adapter = tocAdapter

        tocScrollbarHelper = ScrollbarHelper(
            tocList,
            findViewById(R.id.tocScrollTrack),
            findViewById(R.id.tocScrollThumb),
            findViewById(R.id.tocScrollbarArea)
        )
    }

    private fun showToc() {
        tocAdapter.notifyDataSetChanged()
        tocContainer.visibility = View.VISIBLE
        val currentTocPosition = tocChapters.indexOfFirst { it.index == currentChapter }
        if (currentTocPosition >= 0) {
            val layoutManager = tocList.layoutManager as LinearLayoutManager
            layoutManager.scrollToPositionWithOffset(currentTocPosition, tocList.height / 2 - 50)
        }
        tocList.post { tocScrollbarHelper.update() }
    }

    private fun hideToc() {
        tocContainer.visibility = View.GONE
    }

    private fun handleLink(url: String): Boolean {
        if (url.startsWith("http") && !url.startsWith("https://epub.local/")) return true
        val href = url
            .replace("https://epub.local/", "")
            .replace("file:///", "")
            .substringBefore("#")
        if (href.isBlank()) return true
        resolveChapterIndex(href)?.let { loadChapter(it, 0); return true }
        return true
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    private fun saveProgress() {
        val isFinished = currentChapter >= book.contents.size - 1
            && currentPage >= totalPages - 1
        getSharedPreferences("reading_progress", MODE_PRIVATE).edit()
            .putInt("${bookPath}_chapter", currentChapter)
            .putInt("${bookPath}_page", currentPage)
            .putString("current_book", bookPath)
            .putBoolean("${bookPath}_finished", isFinished)
            .apply()
    }

    private fun getProgress(): Int =
        getSharedPreferences("reading_progress", MODE_PRIVATE)
            .getInt("${bookPath}_chapter", 0)

    private fun getSavedPage(): Int =
        getSharedPreferences("reading_progress", MODE_PRIVATE)
            .getInt("${bookPath}_page", 0)

    private fun loadChapter(index: Int, page: Int = 0, targetAnchor: String = "") {
        if (index < 0 || index >= book.contents.size) return

        currentChapter = index
        currentPage = if (page == GO_TO_LAST_PAGE) 0 else page
        val goToLastPage = page == GO_TO_LAST_PAGE
        isFootnoteVisible = false
        progressBarContainer.visibility = View.VISIBLE

        scope.launch {
            try {
                val rawHtml = withContext(Dispatchers.IO) {
                    String(book.contents[index].data)
                }
                currentChapterRawHtml = rawHtml
                val (bodyHtml, alignmentCss) = withContext(Dispatchers.IO) {
                    HtmlPreprocessor.preprocessHtml(rawHtml, book)
                }

                webView.loadDataWithBaseURL(
                    "https://epub.local/",
                    ReaderTemplate.build(bodyHtml, alignmentCss, goToLastPage, currentPage, targetAnchor),
                    "text/html", "UTF-8", null
                )
                saveProgress()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@ReaderActivity, R.string.error_loading_chapter, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        scrollEndHandler.removeCallbacks(scrollEndRunnable)
        tocScrollbarHelper.destroy()
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onPageCountReady(count: Int) {
            runOnUiThread {
                totalPages = count
                if (currentPage >= totalPages) currentPage = totalPages - 1
                updateProgressBar()
                if (hideTocAfterLoad) {
                    hideTocAfterLoad = false
                    hideToc()
                }
            }
        }

        @JavascriptInterface
        fun onGoToLastPage(page: Int, total: Int) {
            runOnUiThread {
                currentPage = page
                totalPages = total
                updateProgressBar()
                saveProgress()
            }
        }

        @JavascriptInterface
        fun onFootnoteLinkClicked(fragment: String, file: String) {
            val content = HtmlPreprocessor.findFootnoteContent(
                fragment, file, book, currentChapterRawHtml
            )

            if (content.isBlank()) {
                if (file.isNotBlank()) {
                    val index = resolveChapterIndex(file)
                    if (index != null) {
                        runOnUiThread {
                            currentPage = 0
                            loadChapter(index, 0, fragment)
                        }
                    }
                } else {
                    val escaped = fragment
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                    runOnUiThread {
                        webView.evaluateJavascript("navigateToAnchor('$escaped')", null)
                    }
                }
                return
            }

            val escaped = content
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", " ")
            runOnUiThread {
                isFootnoteVisible = true
                progressBarContainer.visibility = View.INVISIBLE
                webView.evaluateJavascript("showFootnote('$escaped')", null)
            }
        }

        @JavascriptInterface
        fun onFootnoteDismissed() {
            runOnUiThread {
                isFootnoteVisible = false
                progressBarContainer.visibility = View.VISIBLE
            }
        }

        @JavascriptInterface
        fun onAnchorNavigated(page: Int) {
            runOnUiThread {
                currentPage = page
                updateProgressBar()
                saveProgress()
            }
        }

        @JavascriptInterface
        fun onImageOverlayShown() {
            runOnUiThread {
                isImageOverlayVisible = true
                progressBarContainer.visibility = View.INVISIBLE
            }
        }

        @JavascriptInterface
        fun onImageOverlayDismissed() {
            runOnUiThread {
                isImageOverlayVisible = false
                progressBarContainer.visibility = View.VISIBLE
            }
        }
    }
}
