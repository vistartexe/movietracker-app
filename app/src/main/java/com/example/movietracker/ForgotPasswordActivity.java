package com.example.movietracker; // sesuaikan dengan package aplikasi Anda

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etUsername, etNewPassword, etConfirmPassword;
    private Button btnResetPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgotpassword);

        // 1. Pengait UI dengan Logic menggunakan findViewById
        etUsername = findViewById(R.id.etUsername);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnResetPassword = findViewById(R.id.btnResetPassword);

        // 2. Event Listener saat tombol Reset Password diklik
        btnResetPassword.setOnClickListener(v -> resetPassword());
    }

    private void resetPassword() {
        String username = etUsername.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validasi Input
        if (username.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Semua kolom harus diisi!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            Toast.makeText(this, "Konfirmasi password tidak cocok!", Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: Panggil fungsi DatabaseHelper / API backend Anda di sini untuk memperbarui password di database
        // Contoh sederhana:
        boolean isUpdated = true; // ganti dengan logika pembaruan DB asli (misal: dbHelper.updatePassword(username, newPassword))

        if (isUpdated) {
            Toast.makeText(this, "Password berhasil diubah! Silakan login.", Toast.LENGTH_LONG).show();

            // 3. Kembali ke LoginActivity menggunakan Explicit Intent
            Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
            startActivity(intent);
            finish(); // Tutup ForgotPasswordActivity agar tidak menumpuk di stack
        } else {
            Toast.makeText(this, "Gagal mengubah password. User tidak ditemukan.", Toast.LENGTH_SHORT).show();
        }

    }
}