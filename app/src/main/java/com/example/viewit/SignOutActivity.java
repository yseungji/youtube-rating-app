package com.example.viewit;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.viewit.databinding.ActivitySignOutBinding;
import com.google.firebase.auth.FirebaseAuth;

public class SignOutActivity extends AppCompatActivity {

    private ActivitySignOutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());

        binding = ActivitySignOutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        String userProfile = intent.getStringExtra("USER_PROFILE");
        binding.textview.setText(userProfile);

        binding.signOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signout();
                finish();
            }
        });
    }

    private void signout() {
        FirebaseAuth.getInstance().signOut();
    }
}
