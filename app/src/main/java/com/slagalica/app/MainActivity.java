package com.slagalica.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.slagalica.app.ui.HomeActivity;
import com.slagalica.app.ui.auth.LoginActivity;
import com.slagalica.app.util.InviteManager;
import com.slagalica.app.util.UserStatusManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            InviteManager.get().startListening();
            UserStatusManager.goOnline(FirebaseAuth.getInstance());
            startActivity(new Intent(this, HomeActivity.class));
        } else {
            startActivity(new Intent(this, LoginActivity.class));
        }
        finish();
    }
}
