package com.example.fix;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class DashboardResponse {
    @SerializedName("students")
    private List<Student> students;

    // Constructor
    public DashboardResponse(List<Student> students) {
        this.students = students;
    }

    // Getter for students
    public List<Student> getStudents() {
        return students;
    }

    // Setter for students
    public void setStudents(List<Student> students) {
        this.students = students;
    }
}