package io.businesscare.app.ui.booking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.businesscare.app.R
import io.businesscare.app.data.model.ServiceSummaryDto
import io.businesscare.app.databinding.ItemServiceEventBinding 

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
        private val onItemClickedListener: (ServiceSummaryDto) -> Unit,
        private val onBookClickedListener: (ServiceSummaryDto) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: ServiceSummaryDto? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.let { item ->
                    onItemClickedListener(item)
                }
            }

            binding.buttonBookService.setOnClickListener { 
                currentItem?.let { item ->
                    onBookClickedListener(item)
                }
            }
        }

        fun bind(item: ServiceSummaryDto) {
            currentItem = item
            val context = itemView.context

            binding.textViewItemTitle.text = item.title 
            binding.textViewItemCategory.text = item.category.uppercase() 
            binding.textViewItemDescription.text = item.description ?: context.getString(R.string.service_no_description) 
            binding.textViewItemProvider.text = item.providerName ?: context.getString(R.string.not_specified) 
            binding.textViewItemDuration.text = formatDuration(item.realisationTime) 
            binding.textViewItemPrice.text = formatPrice(item.price) 
        }

        private fun formatDuration(duration: String?): String {
            return duration ?: itemView.context.getString(R.string.not_specified)
        }

        private fun formatPrice(price: Double?): String {
            val context = itemView.context
            return when {
                price == null -> context.getString(R.string.price_free)
                price <= 0 -> context.getString(R.string.price_free)
                else -> context.getString(R.string.price_format_eur, price)
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