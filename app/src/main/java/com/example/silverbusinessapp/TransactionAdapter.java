package com.example.silverbusinessapp;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Map;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private ArrayList<Map<String, Object>> transactionList;
    private DecimalFormat decimalFormat;

    public TransactionAdapter(ArrayList<Map<String, Object>> transactionList) {
        this.transactionList = transactionList;
        this.decimalFormat = new DecimalFormat("#,##0.00");
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_finaltransaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Map<String, Object> transaction = transactionList.get(position);

        String name = (String) transaction.get("name");
        double debit = (Double) transaction.get("debit");
        double credit = (Double) transaction.get("credit");
        boolean isCashRemaining = (Boolean) transaction.get("isCashRemaining");

        holder.tvTransactionName.setText(name);

        // Set Debit amount with GREEN color
        if (debit > 0.0) {
            holder.tvDebit.setText("₹" + decimalFormat.format(debit));
            holder.tvDebit.setTextColor(Color.parseColor("#43A047")); // Green color
            holder.tvDebit.setVisibility(View.VISIBLE);
        } else {
            holder.tvDebit.setText("");
            holder.tvDebit.setVisibility(View.INVISIBLE);
        }

        // Set Credit amount with RED color
        if (credit > 0.0) {
            holder.tvCredit.setText("₹" + decimalFormat.format(credit));
            holder.tvCredit.setTextColor(Color.parseColor("#E53935")); // Red color
            holder.tvCredit.setVisibility(View.VISIBLE);
        } else {
            holder.tvCredit.setText("");
            holder.tvCredit.setVisibility(View.INVISIBLE);
        }

        // Make cash remaining items italic
        if (isCashRemaining) {
            holder.tvTransactionName.setTypeface(null, Typeface.ITALIC);
            holder.tvDebit.setTypeface(null, Typeface.ITALIC);
            holder.tvCredit.setTypeface(null, Typeface.ITALIC);
        } else {
            holder.tvTransactionName.setTypeface(null, Typeface.NORMAL);
            holder.tvDebit.setTypeface(null, Typeface.NORMAL);
            holder.tvCredit.setTypeface(null, Typeface.NORMAL);
        }
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    public void updateData(ArrayList<Map<String, Object>> newData) {
        this.transactionList = newData;
        notifyDataSetChanged();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView tvTransactionName, tvDebit, tvCredit;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTransactionName = itemView.findViewById(R.id.tv_transaction_name);
            tvDebit = itemView.findViewById(R.id.tv_debit);
            tvCredit = itemView.findViewById(R.id.tv_credit);
        }
    }
}