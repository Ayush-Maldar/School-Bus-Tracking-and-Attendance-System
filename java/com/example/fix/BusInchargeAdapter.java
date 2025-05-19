package com.example.fix;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class BusInchargeAdapter extends ArrayAdapter<BusIncharge> {

    public BusInchargeAdapter(Context context, List<BusIncharge> busIncharges) {
        super(context, 0, busIncharges);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the bus incharge item for this position
        BusIncharge busIncharge = getItem(position);

        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        }

        // Lookup view for data population
        TextView textView = convertView.findViewById(android.R.id.text1);

        // Populate the data into the template view using the data object
        textView.setText(busIncharge.getName());

        // Return the completed view to render on screen
        return convertView;
    }
}