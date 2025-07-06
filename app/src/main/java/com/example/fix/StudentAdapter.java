package com.example.fix;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class StudentAdapter extends ArrayAdapter<Student> {

    public StudentAdapter(Context context, List<Student> students) {
        super(context, 0, students);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the student item for this position
        Student student = getItem(position);

        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        }

        // Lookup view for data population
        TextView textView = convertView.findViewById(android.R.id.text1);

        // Populate the data into the template view using the data object
        textView.setText(student.getName());

        // Return the completed view to render on screen
        return convertView;
    }
    public void updateData(List<Student> newStudentList) {
        // Clear the adapter's internal list using the inherited clear() method
        clear();

        // Add all items from the new list (if not null) using the inherited addAll() method
        if (newStudentList != null) {
            addAll(newStudentList);
        }

        // Notify the adapter that the underlying data has changed
        // This triggers the ListView to refresh.
        notifyDataSetChanged();

        // Optional logging: Use getCount() for ArrayAdapter size
        Log.d("StudentAdapter", "Adapter data updated. New size: " + getCount());
    }

}