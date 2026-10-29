package com.example.silverbusinessapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder> {

    private List<Customer> customerList;
    private OnCustomerActionListener actionListener;

    // Interface for handling actions
    public interface OnCustomerActionListener {
        void onEditCustomer(Customer customer);
        void onDeleteCustomer(Customer customer);
    }

    public CustomerAdapter(List<Customer> customerList) {
        this.customerList = customerList;
    }

    public void setOnCustomerActionListener(OnCustomerActionListener listener) {
        this.actionListener = listener;
    }

    public void updateData(List<Customer> newCustomerList) {
        this.customerList = newCustomerList;
        notifyDataSetChanged();
    }

    public static class CustomerViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTransactionCount;
        ImageButton btnEdit, btnDelete;

        public CustomerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvTransactionCount = itemView.findViewById(R.id.tv_transaction_count);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }

    @NonNull
    @Override
    public CustomerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        return new CustomerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomerViewHolder holder, int position) {
        Customer customer = customerList.get(position);

        // Set customer name
        holder.tvName.setText(customer.getName());

        // Set transaction count
        holder.tvTransactionCount.setText(String.valueOf(customer.getTransactionCount()));

        // Set click listeners
        holder.btnEdit.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onEditCustomer(customer);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onDeleteCustomer(customer);
            }
        });
    }

    @Override
    public int getItemCount() {
        return customerList.size();
    }
}