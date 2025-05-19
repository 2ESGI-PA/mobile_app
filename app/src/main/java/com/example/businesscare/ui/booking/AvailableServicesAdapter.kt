package com.example.businesscare.ui.booking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.businesscare.data.model.ServiceSummaryDto
import com.example.businesscare.databinding.ItemServiceEventBinding
import java.util.Locale

class AvailableServicesAdapter(
    private val onItemClicked: (ServiceSummaryDto) -> Unit,
    private val onBookClicked: (ServiceSummaryDto) -> Unit = onItemClicked
) : ListAdapter<ServiceSummaryDto, AvailableServicesAdapter.ServiceViewHolder>(ServiceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val binding = ItemServiceEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServiceViewHolder(binding, onItemClicked, onBookClicked)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ServiceViewHolder(
        private val binding: ItemServiceEventBinding,
        private val onItemClicked: (ServiceSummaryDto) -> Unit,
        private val onBookClicked: (ServiceSummaryDto) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: ServiceSummaryDto? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.let { item ->
                    onItemClicked(item)
                }
            }

            binding.btnBookService.setOnClickListener {
                currentItem?.let { item ->
                    onBookClicked(item)
                }
            }
        }

        fun bind(item: ServiceSummaryDto) {
            currentItem = item

            binding.tvItemTitle.text = item.title
            item.category?.let { category ->
                binding.tvItemCategory.text = category.uppercase(Locale.getDefault())
            } ?: run {
                binding.tvItemCategory.text = ""
            }
            binding.tvItemDescription.text = item.description ?: "Pas de description disponible"
            binding.tvItemProvider.text = item.providerName ?: "Non spécifié"
            binding.tvItemDuration.text = formatDuration(item.realisationTime)
            binding.tvItemPrice.text = formatPrice(item.price)
        }

        private fun formatDuration(duration: String?): String {
            return duration ?: "Non spécifié"
        }

        private fun formatPrice(price: Double?): String {
            return when {
                price == null -> "Gratuit"
                price <= 0 -> "Gratuit"
                else -> "%.2f€".format(Locale.FRANCE, price)
            }
        }
    }

    class ServiceDiffCallback : DiffUtil.ItemCallback<ServiceSummaryDto>() {
        override fun areItemsTheSame(oldItem: ServiceSummaryDto, newItem: ServiceSummaryDto): Boolean {
            return oldItem.id == newItem.id && oldItem.category == newItem.category
        }

        override fun areContentsTheSame(oldItem: ServiceSummaryDto, newItem: ServiceSummaryDto): Boolean {
            return oldItem == newItem
        }
    }
}