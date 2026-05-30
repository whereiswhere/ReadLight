package com.readlight

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.documentnode.epub4j.epub.EpubReader
import java.io.File
import java.io.FileInputStream
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var bookList: RecyclerView
    private lateinit var permissionView: View
    private lateinit var loadingView: View
    private val books = mutableListOf<BookInfo>()
    private lateinit var adapter: BookAdapter
    private lateinit var booksDir: File

    private val bookCache = mutableMapOf<String, BookInfo>()
    private var lastLoadedFiles = emptySet<String>()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var scrollbarHelper: ScrollbarHelper

    private var currentBookPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        booksDir = File(Environment.getExternalStorageDirectory(), "Books")

        bookList = findViewById(R.id.bookList)
        permissionView = findViewById(R.id.permissionView)
        loadingView = findViewById(R.id.loadingView)

        bookList.layoutManager = LinearLayoutManager(this)
        adapter = BookAdapter(
            books,
            ::getBookProgress,
            { currentBookPath }
        ) { bookInfo ->
            val intent = Intent(this, ReaderActivity::class.java)
            intent.putExtra("book_path", bookInfo.file.absolutePath)
            startActivity(intent)
        }
        bookList.adapter = adapter

        scrollbarHelper = ScrollbarHelper(
            bookList,
            findViewById(R.id.scrollTrack),
            findViewById(R.id.scrollThumb),
            findViewById(R.id.scrollbarArea)
        )

        findViewById<TextView>(R.id.btnGrantPermission).setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${packageName}")
            startActivity(intent)
        }

        checkPermissionAndLoad()
    }

    private fun metaPrefs() = getSharedPreferences("book_meta_cache", MODE_PRIVATE)

    private fun loadMetaFromDisk(file: File): BookInfo? {
        val prefs = metaPrefs()
        val k = file.absolutePath
        val savedModified = prefs.getLong("${k}_modified", -1L)
        if (savedModified != file.lastModified()) return null
        val title = prefs.getString("${k}_title", null) ?: return null
        val author = prefs.getString("${k}_author", "") ?: ""
        val chapters = prefs.getInt("${k}_chapters", 1)
        return BookInfo(file, title, author, chapters)
    }

    private fun saveMetaToDisk(file: File, info: BookInfo) {
        metaPrefs().edit()
            .putLong("${file.absolutePath}_modified", file.lastModified())
            .putString("${file.absolutePath}_title", info.title)
            .putString("${file.absolutePath}_author", info.author)
            .putInt("${file.absolutePath}_chapters", info.totalChapters)
            .apply()
    }

    private fun pruneStaleMetaCache(existingPaths: Set<String>) {
        val prefs = metaPrefs()
        val staleKeys = prefs.all.keys
            .mapNotNull { key ->
                val suffixes = listOf("_modified", "_title", "_author", "_chapters")
                suffixes.firstNotNullOfOrNull { suffix ->
                    if (key.endsWith(suffix)) key.removeSuffix(suffix) else null
                }
            }
            .toSet()
            .filter { path -> path !in existingPaths }

        if (staleKeys.isNotEmpty()) {
            val editor = prefs.edit()
            staleKeys.forEach { path ->
                editor.remove("${path}_modified")
                editor.remove("${path}_title")
                editor.remove("${path}_author")
                editor.remove("${path}_chapters")
            }
            editor.apply()
        }
    }

    private fun refreshCurrentBookPath() {
        currentBookPath = getSharedPreferences("reading_progress", MODE_PRIVATE)
            .getString("current_book", "") ?: ""
    }

    private fun checkPermissionAndLoad() {
        if (hasPermission()) {
            showBookList()
            booksDir.mkdirs()
            refreshCurrentBookPath()
            loadBooks()
        } else {
            showPermissionView()
        }
    }

    private fun showBookList() {
        permissionView.visibility = View.GONE
        loadingView.visibility = View.GONE
        findViewById<View>(R.id.bookListContainer).visibility = View.VISIBLE
    }

    private fun showPermissionView() {
        findViewById<View>(R.id.bookListContainer).visibility = View.GONE
        loadingView.visibility = View.GONE
        permissionView.visibility = View.VISIBLE
    }

    private fun showLoadingView() {
        permissionView.visibility = View.GONE
        findViewById<View>(R.id.bookListContainer).visibility = View.GONE
        loadingView.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) {
            refreshCurrentBookPath()
            refreshBooksIfNeeded()
        } else {
            showPermissionView()
        }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        val keyCode = event?.keyCode ?: return super.dispatchKeyEvent(event)

        if ((keyCode == LightPhoneKeys.SCROLL_UP || keyCode == LightPhoneKeys.SCROLL_DOWN)
            && event.action == KeyEvent.ACTION_DOWN
        ) {
            BrightnessHelper.handleScrollKey(this, keyCode)
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun getBookProgress(bookPath: String, totalChapters: Int): Float {
        val prefs = getSharedPreferences("reading_progress", MODE_PRIVATE)
        if (prefs.getBoolean("${bookPath}_finished", false)) return 1.0f
        val chapter = prefs.getInt("${bookPath}_chapter", 0)
        if (totalChapters <= 0) return 0f
        return chapter.toFloat() / totalChapters
    }

    private fun refreshBooksIfNeeded() {
        if (!booksDir.exists()) {
            booksDir.mkdirs()
            return
        }

        val currentFiles = booksDir.listFiles { f -> f.extension.lowercase() == "epub" }
            ?.map { it.absolutePath }?.toSet() ?: emptySet()

        if (currentFiles != lastLoadedFiles) {
            loadBooks()
        } else {
            showBookList()
            adapter.notifyDataSetChanged()
        }
    }

    private fun loadBooks() {
        if (!booksDir.exists()) {
            booksDir.mkdirs()
            return
        }

        val files = booksDir.listFiles { f -> f.extension.lowercase() == "epub" } ?: return
        lastLoadedFiles = files.map { it.absolutePath }.toSet()

        val cachedBooks = mutableListOf<BookInfo>()
        val needsParsing = mutableListOf<File>()

        files.forEach { file ->
            val info = bookCache[file.absolutePath]
                ?: loadMetaFromDisk(file)?.also { bookCache[file.absolutePath] = it }

            if (info != null) cachedBooks.add(info)
            else needsParsing.add(file)
        }

        if (needsParsing.isEmpty()) {
            books.clear()
            books.addAll(cachedBooks)
            showBookList()
            adapter.notifyDataSetChanged()
            pruneStaleMetaCache(lastLoadedFiles)
            return
        }

        if (cachedBooks.isEmpty()) {
            showLoadingView()
        } else {
            books.clear()
            books.addAll(cachedBooks)
            showBookList()
            adapter.notifyDataSetChanged()
        }

        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                needsParsing.mapNotNull { file ->
                    try {
                        val epub = EpubReader().readEpub(FileInputStream(file))
                        val title = epub.title?.takeIf { it.isNotBlank() }
                            ?: file.nameWithoutExtension
                        val author = epub.metadata.authors.firstOrNull()?.let {
                            "${it.firstname} ${it.lastname}".trim()
                        } ?: ""
                        BookInfo(file, title, author, epub.contents.size)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        BookInfo(file, file.nameWithoutExtension, "", 1)
                    }
                }
            }

            parsed.forEach { info ->
                bookCache[info.file.absolutePath] = info
                saveMetaToDisk(info.file, info)
            }

            books.clear()
            files.forEach { file ->
                bookCache[file.absolutePath]?.let { books.add(it) }
            }

            showBookList()
            adapter.notifyDataSetChanged()
            pruneStaleMetaCache(lastLoadedFiles)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        scrollbarHelper.destroy()
    }
}
