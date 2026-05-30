package com.readlight

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BookAdapter(
    private val books: List<BookInfo>,
    private val getProgress: (String, Int) -> Float,
    private val getCurrentBook: () -> String,
    private val onClick: (BookInfo) -> Unit
) : RecyclerView.Adapter<BookAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.bookTitle)
        val author: TextView = view.findViewById(R.id.bookAuthor)
        val progress: View = view.findViewById(R.id.bookProgress)
        val progressContainer: View = view.findViewById(R.id.progressContainer)
        val currentIndicator: View = view.findViewById(R.id.currentIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book = books[position]
        holder.title.text = book.title
        holder.author.text = book.author
        holder.author.visibility = if (book.author.isEmpty()) View.GONE else View.VISIBLE

        holder.currentIndicator.visibility =
            if (book.file.absolutePath == getCurrentBook()) View.VISIBLE else View.GONE

        val progress = getProgress(book.file.absolutePath, book.totalChapters)
        val isFinished = progress >= 1.0f

        val widthDp = if (isFinished) 5 else 2
        val density = holder.itemView.context.resources.displayMetrics.density
        holder.progressContainer.layoutParams.width = (widthDp * density).toInt()
        holder.progressContainer.requestLayout()

        holder.progress.post {
            val parent = holder.progress.parent as View
            val params = holder.progress.layoutParams
            params.height = (parent.height * progress).toInt()
            holder.progress.layoutParams = params
        }

        holder.itemView.setOnClickListener { onClick(book) }
    }

    override fun getItemCount() = books.size
}
