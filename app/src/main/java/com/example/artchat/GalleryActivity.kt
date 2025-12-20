package com.example.artchat

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.artchat.databinding.ActivityGalleryBinding
import com.example.artchat.model.DrawingItem

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var adapter: GalleryAdapter
    private val drawings = mutableListOf<DrawingItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadDrawings()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter(drawings) { drawing ->
            onDrawingClicked(drawing)
        }

        binding.recyclerViewDrawings.apply {
            layoutManager = GridLayoutManager(this@GalleryActivity, 2)
            this.adapter = this@GalleryActivity.adapter
        }
    }

    private fun loadDrawings() {
        drawings.clear()

        // TODO: Load actual drawings from storage
        // For now, add dummy data
        drawings.addAll(emptyList<DrawingItem>())

        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener {
            loadDrawings()
        }

        binding.btnShare.setOnClickListener {
            if (drawings.isNotEmpty()) {
                shareDrawing(drawings.first())
            }
        }

        binding.btnDeleteAll.setOnClickListener {
            deleteAllDrawings()
        }
    }

    private fun onDrawingClicked(drawing: DrawingItem) {
        val items = arrayOf("Открыть", "Поделиться", "Удалить", "Отмена")

        AlertDialog.Builder(this)
            .setTitle("Рисунок: ${drawing.title}")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openDrawing(drawing)
                    1 -> shareDrawing(drawing)
                    2 -> deleteDrawing(drawing)
                }
            }
            .show()
    }

    private fun openDrawing(drawing: DrawingItem) {
        Toast.makeText(this, "Открыть: ${drawing.title}", Toast.LENGTH_SHORT).show()
    }

    private fun shareDrawing(drawing: DrawingItem) {
        Toast.makeText(this, "Поделиться: ${drawing.title}", Toast.LENGTH_SHORT).show()
    }

    private fun deleteDrawing(drawing: DrawingItem) {
        AlertDialog.Builder(this)
            .setTitle("Удалить рисунок")
            .setMessage("Вы уверены, что хотите удалить '${drawing.title}'?")
            .setPositiveButton("Удалить") { _, _ ->
                drawings.remove(drawing)
                adapter.notifyDataSetChanged()
                updateEmptyState()
                Toast.makeText(this, "Рисунок удален", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteAllDrawings() {
        if (drawings.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("Удалить все рисунки")
            .setMessage("Вы уверены, что хотите удалить все рисунки (${drawings.size})?")
            .setPositiveButton("Удалить все") { _, _ ->
                drawings.clear()
                adapter.notifyDataSetChanged()
                updateEmptyState()
                Toast.makeText(this, "Все рисунки удалены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateEmptyState() {
        if (drawings.isEmpty()) {
            binding.tvEmpty.visibility = android.view.View.VISIBLE
            binding.recyclerViewDrawings.visibility = android.view.View.GONE
            binding.btnShare.isEnabled = false
            binding.btnDeleteAll.isEnabled = false
        } else {
            binding.tvEmpty.visibility = android.view.View.GONE
            binding.recyclerViewDrawings.visibility = android.view.View.VISIBLE
            binding.btnShare.isEnabled = true
            binding.btnDeleteAll.isEnabled = true
        }
    }
}