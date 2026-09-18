package com.myra.assistant.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.databinding.ItemPrimeContactBinding
import com.myra.assistant.model.PrimeContact

class PrimeContactAdapter(
    private val contacts: MutableList<PrimeContact>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PrimeContactAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemPrimeContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPrimeContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.binding.primeItemName.text = contact.name
        holder.binding.primeItemNumber.text = contact.number
        holder.binding.primeItemDelete.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) {
                onDelete(adapterPos)
            }
        }
    }

    override fun getItemCount(): Int = contacts.size

    fun updateData(newContacts: List<PrimeContact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }
}
