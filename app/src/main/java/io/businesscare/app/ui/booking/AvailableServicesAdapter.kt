package io.businesscare.app.ui.booking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.businesscare.app.R
import io.businesscare.app.data.model.ServiceSummaryDto
import io.businesscare.app.databinding.ItemServiceEventBinding
import java.text.SimpleDateFormat
import java.util.Locale

class AvailableServicesAdapter(
    private val onItemClicked: (ServiceSummaryDto) -> Unit
) : ListAdapter<ServiceSummaryDto, AvailableServicesAdapter.ServiceViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val binding = ItemServiceEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    inner class ServiceViewHolder(private val binding: ItemServiceEventBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClicked(getItem(position))
                }
            }
        }

        fun bind(service: ServiceSummaryDto) {
            binding.apply {
                textViewItemTitle.text = service.title
                textViewItemDescription.text = service.description ?: itemView.context.getString(R.string.no_description_available)
                textViewItemCategory.text = service.category ?: ""
                textViewItemCategory.visibility = if (service.category.isNullOrBlank()) View.GONE else View.VISIBLE

                val providerDisplayName = service.provider?.referenceName ?: service.provider?.fullName
                providerDisplayName?.let {
                    textViewProviderName.text = it
                    textViewProviderName.visibility = View.VISIBLE
                } ?: run {
                    textViewProviderName.visibility = View.GONE
                }

                if (service.isEvent) {
                    iconItemType.setImageResource(R.drawable.ic_event)
                    textViewItemPrice.visibility = View.GONE
                    textViewItemDuration.visibility = View.GONE

                    service.startDate?.let {
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        textViewItemDateTime.text = dateFormat.format(it)
                        textViewItemDateTime.visibility = View.VISIBLE
                    } ?: run {
                        textViewItemDateTime.visibility = View.GONE
                    }
                } else {
                    iconItemType.setImageResource(R.drawable.ic_service)
                    textViewItemDateTime.visibility = View.GONE

                    service.price?.let {
                        textViewItemPrice.text = itemView.context.getString(R.string.price_format, it)
                        textViewItemPrice.visibility = View.VISIBLE
                    } ?: run {
                        textViewItemPrice.visibility = View.GONE
                    }

                    service.realisationTime?.let {
                        textViewItemDuration.text = it
                        textViewItemDuration.visibility = View.VISIBLE
                    } ?: run {
                        textViewItemDuration.visibility = View.GONE
                    }
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ServiceSummaryDto>() {
        override fun areItemsTheSame(oldItem: ServiceSummaryDto, newItem: ServiceSummaryDto) =
            oldItem.id == newItem.id && oldItem.isEvent == newItem.isEvent

        override fun areContentsTheSame(oldItem: ServiceSummaryDto, newItem: ServiceSummaryDto) =
            oldItem == newItem
    }
}