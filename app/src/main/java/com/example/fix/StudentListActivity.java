package com.example.fix;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StudentListActivity extends AppCompatActivity {

    private ListView studentListView;
    private StudentAdapter studentAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Make sure the layout file exists and is correct
        setContentView(R.layout.activity_parent);

        studentListView = findViewById(R.id.students);

        // Retrieve token from SharedPreferences (or another source)
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String token = prefs.getString("token", "");
        // Prepend "Bearer " if your backend expects it:
        String authToken = "Bearer " + token;

        // Fetch data from the API using Retrofit
        ApiService apiService = Retrofitclient.getClient().create(ApiService.class);
        Call<List<Student>> call = apiService.getStudents(authToken);
        call.enqueue(new Callback<List<Student>>() {
            @Override
            public void onResponse(Call<List<Student>> call, Response<List<Student>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Student> students = response.body();
                    // Set up the adapter with the list of students
                    studentAdapter = new StudentAdapter(StudentListActivity.this, students);
                    studentListView.setAdapter(studentAdapter);
                } else {
                    Toast.makeText(StudentListActivity.this, "Failed to load students", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Student>> call, Throwable t) {
                Toast.makeText(StudentListActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
