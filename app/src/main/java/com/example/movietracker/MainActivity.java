package com.example.movietracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.movietracker.utils.Constants;
import com.example.movietracker.utils.SessionManager;

public class MainActivity extends AppCompatActivity {

    private int userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Terima data dari LoginActivity
        userId = getIntent().getIntExtra(Constants.EXTRA_USER_ID, -1);
        String username = getIntent().getStringExtra(Constants.EXTRA_USERNAME);
        String fullName = getIntent().getStringExtra(Constants.EXTRA_FULL_NAME);

        // Pulihkan sesi apabila Activity dibuat ulang tanpa Intent extras.
        SessionManager session = new SessionManager(this);
        if (userId == -1 && session.isLoggedIn()) {
            userId = session.getUserId();
            username = session.getUsername();
            fullName = session.getFullName();
        }
        if (userId == -1) {
            SessionManager.redirectToLogin(this);
            return;
        }

        try {
            setupHome(username, fullName);
        } catch (RuntimeException e) {
            // Jangan biarkan aplikasi tertutup tanpa pesan: tampilkan penyebabnya.
            android.util.Log.e("MainActivity", "Gagal menampilkan halaman Home", e);
            android.widget.Toast.makeText(this,
                    "Gagal membuka Home: " + e.getClass().getSimpleName() + " - " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void setupHome(String username, String fullName) {
        setContentView(R.layout.activity_main);

        // Sapaan memakai kata pertama dari nama lengkap, misalnya "Andi"
        String displayName = username;
        if (fullName != null && !fullName.trim().isEmpty()) {
            displayName = fullName.trim().split("\\s+")[0];
        }
        if (displayName == null) {
            displayName = "";
        }

        TextView tvGreeting = findViewById(R.id.tvGreeting);
        tvGreeting.setText("Halo, " + displayName + "!");

        // ALUR 2: Home -> Genre
        findViewById(R.id.btnPickMovie).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GenreActivity.class);
            intent.putExtra(Constants.EXTRA_USER_ID, userId);
            startActivity(intent);
        });

        // Home -> Favorit
        findViewById(R.id.btnMyFavorites).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, FavoriteActivity.class);
            intent.putExtra(Constants.EXTRA_USER_ID, userId);
            startActivity(intent);
        });

        findViewById(R.id.btnFilter).setOnClickListener(v -> {
            Intent i = new Intent(this, FeatureActivity.class); i.putExtra("mode", "Filter"); i.putExtra(Constants.EXTRA_USER_ID, userId); startActivity(i);
        });
        findViewById(R.id.btnNobar).setOnClickListener(v -> {
            Intent i = new Intent(this, FeatureActivity.class); i.putExtra("mode", "Nobar"); i.putExtra(Constants.EXTRA_USER_ID, userId); startActivity(i);
        });
        findViewById(R.id.btnAi).setOnClickListener(v -> {
            Intent i = new Intent(this, FeatureActivity.class); i.putExtra("mode", "Ai"); i.putExtra(Constants.EXTRA_USER_ID, userId); startActivity(i);
        });
        findViewById(R.id.btnPlus).setOnClickListener(v -> {
            Intent i = new Intent(this, FeatureActivity.class); i.putExtra("mode", "Plus"); i.putExtra(Constants.EXTRA_USER_ID, userId); startActivity(i);
        });
        findViewById(R.id.btnHistory).setOnClickListener(v -> {
            Intent i = new Intent(this, FeatureActivity.class); i.putExtra("mode", "History"); i.putExtra(Constants.EXTRA_USER_ID, userId); startActivity(i);
        });
        findViewById(R.id.navHome).setOnClickListener(v -> ((android.widget.ScrollView)findViewById(R.id.homeScroll)).smoothScrollTo(0,0));
        findViewById(R.id.navExplore).setOnClickListener(v -> findViewById(R.id.btnPickMovie).performClick());
        findViewById(R.id.navNobar).setOnClickListener(v -> findViewById(R.id.btnNobar).performClick());
        findViewById(R.id.navFavorite).setOnClickListener(v -> findViewById(R.id.btnMyFavorites).performClick());
        findViewById(R.id.navAi).setOnClickListener(v -> findViewById(R.id.btnAi).performClick());
        // Logout: hapus sesi, kembali ke Login, kosongkan tumpukan Activity
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            new SessionManager(MainActivity.this).logout();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

}
