package com.example.fix;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class User_type extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_user_type);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button parent=findViewById(R.id.parent);
        Button bus_incharge=findViewById(R.id.bus_incharge);
        Button school_authority=findViewById(R.id.school_authority);

        parent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=new Intent(User_type.this, Sign_in.class);
                intent.putExtra("user_type","parent");
                startActivity(intent);
            }
        });

        bus_incharge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=new Intent(User_type.this, Sign_in.class);
                intent.putExtra("user_type","bus_incharge");
                startActivity(intent);
            }
        });

        school_authority.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=new Intent(User_type.this, Sign_in.class);
                intent.putExtra("user_type","school_authority");
                startActivity(intent);
            }
        });
    }
}