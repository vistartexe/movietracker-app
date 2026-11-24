package com.example.movietracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.movietracker.database.DatabaseHelper;
import com.example.movietracker.model.Movie;
import com.example.movietracker.utils.Constants;
import com.example.movietracker.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.*;

public class FeatureActivity extends AppCompatActivity {
    private LinearLayout body;
    private int user;
    private SharedPreferences prefs;
    private List<Movie> movies;
    private final Map<String, Integer> durations = new HashMap<>();
    private String currentRoomCode;
    private int votingIndex;
    private boolean busy;
    private boolean isPlaying = true;

    // Firebase Multiplayer Listeners
    private ListenerRegistration roomListener;
    private ListenerRegistration participantsListener;
    private ListenerRegistration votesListener;

    private FirebaseFirestore getDb() {
        return FirebaseFirestore.getInstance();
    }

    private FirebaseAuth getAuth() {
        return FirebaseAuth.getInstance();
    }

    private String getMyUid() {
        FirebaseUser u = getAuth().getCurrentUser();
        if (u != null) return u.getUid();
        return "user_" + user;
    }

    private String getMyName() {
        SessionManager s = new SessionManager(this);
        String name = s.getFullName();
        if (name == null || name.trim().isEmpty()) {
            name = s.getUsername();
        }
        if (name == null || name.trim().isEmpty()) {
            name = "Peserta " + user;
        }
        return name;
    }

    private void ensureAuth(Runnable onAuthReady) {
        FirebaseAuth auth = getAuth();
        if (auth.getCurrentUser() != null) {
            onAuthReady.run();
        } else {
            auth.signInAnonymously().addOnCompleteListener(task -> onAuthReady.run());
        }
    }

    private void clearListeners() {
        if (roomListener != null) { roomListener.remove(); roomListener = null; }
        if (participantsListener != null) { participantsListener.remove(); participantsListener = null; }
        if (votesListener != null) { votesListener.remove(); votesListener = null; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearListeners();
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        user = getIntent().getIntExtra(Constants.EXTRA_USER_ID, -1);
        if (user < 0 || !new SessionManager(this).isLoggedIn()) {
            SessionManager.redirectToLogin(this);
            return;
        }
        prefs = getSharedPreferences("features_" + user, MODE_PRIVATE);
        movies = new DatabaseHelper(this).getAllMovies();
        String[] names = {"Mad Max: Fury Road", "John Wick", "The Dark Knight", "Superbad", "The Hangover", "Home Alone", "Get Out", "The Conjuring", "A Quiet Place", "Titanic", "The Notebook", "La La Land", "Inception", "Interstellar", "The Matrix"};
        int[] mins = {120, 101, 152, 113, 100, 103, 104, 112, 90, 194, 123, 128, 148, 169, 136};
        for (int i = 0; i < names.length; i++) durations.put(names[i], mins[i]);
        String mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "Filter";
        switch (mode) {
            case "Nobar": nobar(); break;
            case "Ai": ai(); break;
            case "Plus": plus(); break;
            case "History": history(); break;
            default: filter();
        }
    }

    private void page(String title) {
        clearListeners();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg_dark));
        scroll.setFitsSystemWindows(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        scroll.addView(body);
        setContentView(scroll);
        button("← Kembali", () -> finish());
        TextView t = label(title);
        t.setTextSize(24);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setPadding(0, 12, 0, 12);
        t.setText(s);
        body.addView(t);
        return t;
    }

    private void button(String s, Runnable action) {
        MaterialButton b = new MaterialButton(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setCornerRadius((int) (14 * getResources().getDisplayMetrics().density));
        b.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
        if (s.equals("← Kembali")) b.setIconResource(R.drawable.ic_arrow_back);
        else if (s.contains("Suka")) b.setIconResource(R.drawable.ui_heart);
        else if (s.contains("Room")) b.setIconResource(R.drawable.ui_users);
        else if (s.equals("Kirim")) b.setIconResource(R.drawable.ui_spark);
        else if (s.contains("filter") || s.contains("Filter")) b.setIconResource(R.drawable.ui_filter);
        b.setOnClickListener(v -> action.run());
        body.addView(b);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.LTGRAY);
        body.addView(e);
        return e;
    }

    private Spinner spinner(String... items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        s.setAdapter(a);
        s.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.LTGRAY));
        body.addView(s);
        return s;
    }

    private void detail(Movie m) {
        Intent i = new Intent(this, MovieDetailActivity.class);
        i.putExtra(Constants.EXTRA_USER_ID, user);
        i.putExtra(Constants.EXTRA_MOVIE_ID, m.getId());
        startActivity(i);
    }

    private void filter() {
        page("Filter mood dan durasi");
        label("Suasana hati");
        Spinner mood = spinner("Semua", "Ingin tertawa", "Romantis", "Tegang", "Petualangan", "Penasaran");
        label("Durasi");
        Spinner duration = spinner("Semua", "≤ 100 menit", "101–120 menit", "> 120 menit");
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        button("Terapkan filter", () -> {
            result.removeAllViews();
            int n = 0;
            String[] genres = {"", "Comedy", "Romance", "Horror", "Action", "Sci-Fi"};
            for (Movie m : movies) {
                int d = durations.getOrDefault(m.getTitle(), 0);
                int k = duration.getSelectedItemPosition();
                if (mood.getSelectedItemPosition() != 0 && !m.getGenre().equals(genres[mood.getSelectedItemPosition()])) continue;
                if (k != 0 && (d == 0 || (k == 1 && d > 100) || (k == 2 && (d <= 100 || d > 120)) || (k == 3 && d <= 120))) continue;
                MaterialButton b = new MaterialButton(this);
                b.setText(m.getTitle() + " • " + (d == 0 ? "Durasi belum diketahui" : d + " menit"));
                b.setOnClickListener(v -> detail(m));
                result.addView(b);
                n++;
            }
            if (n == 0) {
                TextView t = new TextView(this);
                t.setTextColor(Color.WHITE);
                t.setText("Tidak ada film yang cocok.");
                result.addView(t);
            }
        });
        body.addView(result);
    }

    // =========================================================================
    // 1. HALAMAN UTAMA NOBAR MATCH
    // =========================================================================
    private void nobar() {
        page("Nobar Match — Multiplayer Real-Time");
        label("Fitur Nobar Match memungkinkan kamu dan teman-teman memilih film yang ingin ditonton bersama secara real-time dari dua atau lebih HP berbeda.");

        button("➕ Buat Room Baru", this::nobarCreateRoom);
        button("🚪 Gabung Room (Pakai Kode)", this::nobarJoinRoom);
        button("📜 Riwayat Nobar", this::history);

        LinearLayout cardInfo = new LinearLayout(this);
        cardInfo.setOrientation(LinearLayout.VERTICAL);
        cardInfo.setBackgroundColor(Color.parseColor("#1E293B"));
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        cardInfo.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 16, 0, 0);
        cardInfo.setLayoutParams(lp);

        TextView tvGuideTitle = new TextView(this);
        tvGuideTitle.setText("💡 Petunjuk Penggunaan Nobar Match");
        tvGuideTitle.setTextColor(Color.parseColor("#38BDF8"));
        tvGuideTitle.setTextSize(15);
        tvGuideTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        cardInfo.addView(tvGuideTitle);

        TextView tvGuideText = new TextView(this);
        tvGuideText.setText("1. Salah satu peserta menekan 'Buat Room Baru' dan menentukan nama, jumlah peserta, & filter.\n" +
                "2. Bagikan 6 Digit Kode Room unik kepada teman.\n" +
                "3. Teman-teman bergabung via 'Gabung Room' dengan memasukkan Kode Room.\n" +
                "4. Setiap peserta menekan tombol 'SIAP' di Lobby. Host menekan 'Mulai Voting'.\n" +
                "5. Pilih Suka/Lewati pada kartu film secara independen.\n" +
                "6. Setelah semua selesai, hasil match 100% dan rekomendasi film terbanyak langsung muncul!");
        tvGuideText.setTextColor(Color.LTGRAY);
        tvGuideText.setTextSize(13);
        tvGuideText.setPadding(0, 8, 0, 0);
        cardInfo.addView(tvGuideText);

        body.addView(cardInfo);
    }

    // =========================================================================
    // 2. HALAMAN BUAT ROOM
    // =========================================================================
    private void nobarCreateRoom() {
        page("Buat Room Nobar Baru");

        label("Nama Room");
        EditText inputRoomName = input("Contoh: Room Nonton Bioskop");

        label("Maksimal Jumlah Peserta");
        Spinner spMax = spinner("2 Peserta", "3 Peserta", "4 Peserta", "5 Peserta", "6 Peserta");

        label("Filter Genre Film");
        Spinner spGenre = spinner("Semua", "Action", "Comedy", "Horror", "Romance", "Sci-Fi");

        label("Filter Suasana Hati / Mood");
        Spinner spMood = spinner("Semua", "Ingin tertawa", "Romantis", "Tegang", "Petualangan", "Penasaran");

        label("Filter Durasi Film");
        Spinner spDuration = spinner("Semua", "≤ 100 menit", "101–120 menit", "> 120 menit");

        button("✨ Buat Room Sekarang", () -> {
            String name = inputRoomName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Isi nama room terlebih dahulu", Toast.LENGTH_SHORT).show();
                return;
            }

            int maxCount = spMax.getSelectedItemPosition() + 2;
            String selectedGenre = spGenre.getSelectedItem().toString();
            String selectedMood = spMood.getSelectedItem().toString();
            String selectedDuration = spDuration.getSelectedItem().toString();

            String generatedCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);

            ensureAuth(() -> {
                String myUid = getMyUid();
                String myName = getMyName();

                Map<String, Object> roomData = new HashMap<>();
                roomData.put("code", generatedCode);
                roomData.put("name", name);
                roomData.put("hostUid", myUid);
                roomData.put("hostName", myName);
                roomData.put("maxParticipants", maxCount);
                roomData.put("genreFilter", selectedGenre);
                roomData.put("moodFilter", selectedMood);
                roomData.put("durationFilter", selectedDuration);
                roomData.put("status", "LOBBY");
                roomData.put("createdAt", System.currentTimeMillis());

                getDb().collection("rooms").document(generatedCode).set(roomData)
                        .addOnSuccessListener(aVoid -> {
                            Map<String, Object> pData = new HashMap<>();
                            pData.put("uid", myUid);
                            pData.put("name", myName);
                            pData.put("isHost", true);
                            pData.put("isReady", true);
                            pData.put("votingFinished", false);
                            pData.put("joinedAt", System.currentTimeMillis());

                            getDb().collection("rooms").document(generatedCode)
                                    .collection("participants").document(myUid).set(pData)
                                    .addOnSuccessListener(aVoid2 -> {
                                        Toast.makeText(this, "Room berhasil dibuat! Kode: " + generatedCode, Toast.LENGTH_SHORT).show();
                                        nobarLobbyRoom(generatedCode);
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Gagal membuat room di Firestore: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            });
        });
    }

    // =========================================================================
    // 3. HALAMAN GABUNG ROOM
    // =========================================================================
    private void nobarJoinRoom() {
        page("Gabung Room Nobar");

        label("Kode Room (6 Karakter)");
        EditText inputCode = input("Masukkan Kode Room (contoh: X7K9P2)");

        button("🚪 Gabung Room", () -> {
            String code = inputCode.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (code.length() < 6) {
                Toast.makeText(this, "Kode room terdiri dari 6 karakter", Toast.LENGTH_SHORT).show();
                return;
            }

            ensureAuth(() -> {
                getDb().collection("rooms").document(code).get().addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Room tidak ditemukan! Periksa kembali kode room.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String status = doc.getString("status");
                    if (!"LOBBY".equals(status)) {
                        Toast.makeText(this, "Room sedang berjalan atau telah selesai!", Toast.LENGTH_LONG).show();
                        return;
                    }

                    long maxP = doc.getLong("maxParticipants") != null ? doc.getLong("maxParticipants") : 4;

                    getDb().collection("rooms").document(code).collection("participants").get().addOnSuccessListener(pSnap -> {
                        if (pSnap.size() >= maxP) {
                            Toast.makeText(this, "Room sudah penuh! Maksimal " + maxP + " peserta.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        String myUid = getMyUid();
                        String myName = getMyName();

                        Map<String, Object> pData = new HashMap<>();
                        pData.put("uid", myUid);
                        pData.put("name", myName);
                        pData.put("isHost", false);
                        pData.put("isReady", false);
                        pData.put("votingFinished", false);
                        pData.put("joinedAt", System.currentTimeMillis());

                        getDb().collection("rooms").document(code).collection("participants").document(myUid).set(pData)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Berhasil bergabung ke room " + code, Toast.LENGTH_SHORT).show();
                                    nobarLobbyRoom(code);
                                });
                    });
                }).addOnFailureListener(e -> Toast.makeText(this, "Gagal menghubungi server: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            });
        });
    }

    // =========================================================================
    // 4. HALAMAN LOBBY ROOM
    // =========================================================================
    private void nobarLobbyRoom(String roomCode) {
        currentRoomCode = roomCode;
        page("Lobby Room — " + roomCode);

        LinearLayout cardHeader = new LinearLayout(this);
        cardHeader.setOrientation(LinearLayout.VERTICAL);
        cardHeader.setBackgroundColor(Color.parseColor("#1E293B"));
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        cardHeader.setPadding(pad, pad, pad, pad);

        TextView tvCode = new TextView(this);
        tvCode.setText("🔑 KODE ROOM: " + roomCode);
        tvCode.setTextColor(Color.parseColor("#38BDF8"));
        tvCode.setTextSize(18);
        tvCode.setTypeface(null, android.graphics.Typeface.BOLD);
        cardHeader.addView(tvCode);

        TextView tvRoomName = new TextView(this);
        tvRoomName.setText("Memuat data room...");
        tvRoomName.setTextColor(Color.WHITE);
        tvRoomName.setTextSize(15);
        tvRoomName.setPadding(0, 4, 0, 8);
        cardHeader.addView(tvRoomName);

        LinearLayout rowCopy = new LinearLayout(this);
        rowCopy.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton btnCopy = new MaterialButton(this);
        btnCopy.setText("📋 Salin Kode");
        btnCopy.setAllCaps(false);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Room Code", roomCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Kode Room disalin ke clipboard!", Toast.LENGTH_SHORT).show();
        });
        rowCopy.addView(btnCopy);

        MaterialButton btnShare = new MaterialButton(this);
        btnShare.setText("📲 Bagikan Kode");
        btnShare.setAllCaps(false);
        btnShare.setOnClickListener(v -> {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, "Yuk ikutan Nobar Match di MovieTracker! Kode Room: " + roomCode);
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "Bagikan Kode Room"));
        });
        rowCopy.addView(btnShare);

        cardHeader.addView(rowCopy);
        body.addView(cardHeader);

        TextView tvFilterSummary = label("Filter: Memuat...");

        label("👥 Daftar Peserta di Room");
        LinearLayout participantsContainer = new LinearLayout(this);
        participantsContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(participantsContainer);

        LinearLayout rowActions = new LinearLayout(this);
        rowActions.setOrientation(LinearLayout.VERTICAL);
        body.addView(rowActions);

        final boolean[] myReady = {false};
        final boolean[] isHost = {false};

        // Firestore Realtime Listener: Document Room
        roomListener = getDb().collection("rooms").document(roomCode).addSnapshotListener((doc, e) -> {
            if (e != null || doc == null || !doc.exists()) return;

            String roomName = doc.getString("name");
            String status = doc.getString("status");
            String hostUid = doc.getString("hostUid");
            String hostName = doc.getString("hostName");
            long maxP = doc.getLong("maxParticipants") != null ? doc.getLong("maxParticipants") : 4;
            String gFilter = doc.getString("genreFilter");
            String dFilter = doc.getString("durationFilter");

            tvRoomName.setText("Nama Room: " + roomName + " (Maks. " + maxP + " Peserta)\nHost: " + hostName);
            tvFilterSummary.setText("Filter: Genre " + gFilter + " • Durasi " + dFilter);

            if ("VOTING".equals(status)) {
                clearListeners();
                nobarVotingRoom(roomCode);
                return;
            }

            if (getMyUid().equals(hostUid)) {
                isHost[0] = true;
            }
        });

        // Firestore Realtime Listener: Subcollection Participants
        participantsListener = getDb().collection("rooms").document(roomCode)
                .collection("participants").addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;

                    participantsContainer.removeAllViews();
                    rowActions.removeAllViews();

                    boolean allReady = true;
                    int countP = snap.size();

                    for (DocumentSnapshot pDoc : snap.getDocuments()) {
                        String uid = pDoc.getString("uid");
                        String pName = pDoc.getString("name");
                        Boolean hostFlag = pDoc.getBoolean("isHost");
                        Boolean readyFlag = pDoc.getBoolean("isReady");

                        boolean ready = readyFlag != null && readyFlag;
                        boolean host = hostFlag != null && hostFlag;

                        if (!ready) allReady = false;

                        if (getMyUid().equals(uid)) {
                            myReady[0] = ready;
                        }

                        LinearLayout item = new LinearLayout(this);
                        item.setOrientation(LinearLayout.HORIZONTAL);
                        item.setBackgroundColor(Color.parseColor("#1E293B"));
                        int pPad = (int) (12 * getResources().getDisplayMetrics().density);
                        item.setPadding(pPad, pPad, pPad, pPad);
                        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        itemLp.setMargins(0, 6, 0, 6);
                        item.setLayoutParams(itemLp);

                        TextView tvName = new TextView(this);
                        tvName.setText((host ? "👑 " : "👤 ") + pName + (host ? " (Host)" : ""));
                        tvName.setTextColor(Color.WHITE);
                        tvName.setTextSize(14);
                        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
                        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                        tvName.setLayoutParams(nameLp);
                        item.addView(tvName);

                        TextView tvReady = new TextView(this);
                        tvReady.setText(ready ? "✅ SIAP" : "⏳ BELUM SIAP");
                        tvReady.setTextColor(ready ? Color.GREEN : Color.YELLOW);
                        tvReady.setTextSize(13);
                        item.addView(tvReady);

                        participantsContainer.addView(item);
                    }

                    // Button Actions in Lobby
                    final boolean canStart = allReady && countP >= 2;

                    MaterialButton btnToggleReady = new MaterialButton(this);
                    btnToggleReady.setText(myReady[0] ? "⏳ Ubah Jadi Belum Siap" : "✅ Saya Siap!");
                    btnToggleReady.setAllCaps(false);
                    btnToggleReady.setOnClickListener(v -> {
                        getDb().collection("rooms").document(roomCode)
                                .collection("participants").document(getMyUid())
                                .update("isReady", !myReady[0]);
                    });
                    rowActions.addView(btnToggleReady);

                    if (isHost[0]) {
                        MaterialButton btnStart = new MaterialButton(this);
                        btnStart.setText("🚀 Mulai Voting Film (" + countP + " Peserta)");
                        btnStart.setAllCaps(false);
                        btnStart.setEnabled(canStart);
                        btnStart.setOnClickListener(v -> {
                            if (!canStart) {
                                Toast.makeText(this, "Semua peserta harus SIAP dan minimal 2 peserta di room!", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            getDb().collection("rooms").document(roomCode).update("status", "VOTING");
                        });
                        rowActions.addView(btnStart);
                    }

                    MaterialButton btnLeave = new MaterialButton(this);
                    btnLeave.setText("🚪 Keluar Room");
                    btnLeave.setAllCaps(false);
                    btnLeave.setOnClickListener(v -> {
                        getDb().collection("rooms").document(roomCode)
                                .collection("participants").document(getMyUid()).delete()
                                .addOnSuccessListener(aVoid -> {
                                    clearListeners();
                                    nobar();
                                });
                    });
                    rowActions.addView(btnLeave);
                });
    }

    // =========================================================================
    // 5. HALAMAN VOTING FILM
    // =========================================================================
    private final Map<String, Boolean> localUserVotes = new HashMap<>();

    private void nobarVotingRoom(String roomCode) {
        page("Voting Film Nobar");

        getDb().collection("rooms").document(roomCode).get().addOnSuccessListener(roomDoc -> {
            String gFilter = roomDoc.getString("genreFilter");
            String dFilter = roomDoc.getString("durationFilter");

            // Filter Katalog Lokal
            List<Movie> filtered = new ArrayList<>();

            for (Movie m : movies) {
                int d = durations.getOrDefault(m.getTitle(), 120);
                if (gFilter != null && !"Semua".equals(gFilter) && !m.getGenre().equalsIgnoreCase(gFilter)) continue;
                if (dFilter != null && !"Semua".equals(dFilter)) {
                    if ("≤ 100 menit".equals(dFilter) && d > 100) continue;
                    if ("101–120 menit".equals(dFilter) && (d <= 100 || d > 120)) continue;
                    if ("> 120 menit".equals(dFilter) && d <= 120) continue;
                }
                filtered.add(m);
            }

            if (filtered.isEmpty()) {
                filtered.addAll(movies); // Fallback ke seluruh katalog jika filter terlalu ketat
            }

            votingIndex = 0;
            localUserVotes.clear();
            renderVotingCard(roomCode, filtered);
        });

        // Firestore Realtime Listener: Menunggu Semua Peserta Selesai Voting
        participantsListener = getDb().collection("rooms").document(roomCode)
                .collection("participants").addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;

                    boolean allFinished = true;
                    for (DocumentSnapshot pDoc : snap.getDocuments()) {
                        Boolean fin = pDoc.getBoolean("votingFinished");
                        if (fin == null || !fin) {
                            allFinished = false;
                            break;
                        }
                    }

                    if (allFinished && snap.size() > 0) {
                        getDb().collection("rooms").document(roomCode).update("status", "FINISHED");
                    }
                });

        roomListener = getDb().collection("rooms").document(roomCode).addSnapshotListener((doc, e) -> {
            if (e != null || doc == null || !doc.exists()) return;
            if ("FINISHED".equals(doc.getString("status"))) {
                clearListeners();
                nobarResultRoom(roomCode);
            }
        });
    }

    private void renderVotingCard(String roomCode, List<Movie> filteredList) {
        body.removeAllViews();

        button("← Keluar Voting", () -> nobarLobbyRoom(roomCode));

        if (votingIndex >= filteredList.size()) {
            // User Selesai Voting
            label("✅ Kamu telah selesai memberikan voting!");

            Map<String, Object> voteData = new HashMap<>();
            voteData.put("uid", getMyUid());
            voteData.put("userName", getMyName());
            voteData.put("movieVotes", localUserVotes);

            getDb().collection("rooms").document(roomCode)
                    .collection("votes").document(getMyUid()).set(voteData);

            getDb().collection("rooms").document(roomCode)
                    .collection("participants").document(getMyUid())
                    .update("votingFinished", true);

            LinearLayout cardWait = new LinearLayout(this);
            cardWait.setOrientation(LinearLayout.VERTICAL);
            cardWait.setBackgroundColor(Color.parseColor("#1E293B"));
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            cardWait.setPadding(pad, pad, pad, pad);

            TextView tvWait = new TextView(this);
            tvWait.setText("⏳ Menunggu peserta lain selesai memberikan suara...\nHasil match 100% akan terbuka otomatis di seluruh HP!");
            tvWait.setTextColor(Color.WHITE);
            tvWait.setTextSize(15);
            cardWait.addView(tvWait);

            ProgressBar pb = new ProgressBar(this);
            cardWait.addView(pb);

            body.addView(cardWait);
            return;
        }

        Movie m = filteredList.get(votingIndex);

        TextView tvProgress = label("Film " + (votingIndex + 1) + " dari " + filteredList.size());
        tvProgress.setTextColor(Color.parseColor("#38BDF8"));

        // Frame Kartu Film Modern
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1E1E2E"));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);

        ImageView poster = new ImageView(this);
        int resId = getResources().getIdentifier(m.getPoster(), "drawable", getPackageName());
        if (resId == 0) resId = R.drawable.poster_action;
        poster.setImageResource(resId);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (220 * getResources().getDisplayMetrics().density));
        poster.setLayoutParams(lp);
        poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(poster);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(m.getTitle() + " (" + m.getYear() + ")");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, 12, 0, 4);
        card.addView(tvTitle);

        TextView tvDetails = new TextView(this);
        int dur = durations.getOrDefault(m.getTitle(), 120);
        tvDetails.setText("Genre: " + m.getGenre() + " • " + dur + " Menit • Rating: ★ " + (m.getAverageRating() > 0 ? String.format(Locale.getDefault(), "%.1f", m.getAverageRating()) : "Baru"));
        tvDetails.setTextColor(Color.LTGRAY);
        tvDetails.setTextSize(13);
        tvDetails.setPadding(0, 0, 0, 8);
        card.addView(tvDetails);

        TextView tvSynopsis = new TextView(this);
        tvSynopsis.setText(m.getSynopsis());
        tvSynopsis.setTextColor(Color.WHITE);
        tvSynopsis.setTextSize(13);
        tvSynopsis.setPadding(0, 0, 0, 16);
        card.addView(tvSynopsis);

        body.addView(card);

        LinearLayout rowButtons = new LinearLayout(this);
        rowButtons.setOrientation(LinearLayout.HORIZONTAL);
        rowButtons.setPadding(0, 16, 0, 0);

        MaterialButton btnLike = new MaterialButton(this);
        btnLike.setText("❤️ Suka Film Ini");
        btnLike.setAllCaps(false);
        btnLike.setBackgroundColor(Color.parseColor("#E50914"));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btnLp.setMargins(0, 0, 8, 0);
        btnLike.setLayoutParams(btnLp);
        btnLike.setOnClickListener(v -> {
            localUserVotes.put(String.valueOf(m.getId()), true);
            votingIndex++;
            renderVotingCard(roomCode, filteredList);
        });
        rowButtons.addView(btnLike);

        MaterialButton btnSkip = new MaterialButton(this);
        btnSkip.setText("⏭️ Lewati");
        btnSkip.setAllCaps(false);
        btnSkip.setBackgroundColor(Color.parseColor("#475569"));
        LinearLayout.LayoutParams btnLp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btnLp2.setMargins(8, 0, 0, 0);
        btnSkip.setLayoutParams(btnLp2);
        btnSkip.setOnClickListener(v -> {
            localUserVotes.put(String.valueOf(m.getId()), false);
            votingIndex++;
            renderVotingCard(roomCode, filteredList);
        });
        rowButtons.addView(btnSkip);

        body.addView(rowButtons);
    }

    // =========================================================================
    // 6. HALAMAN HASIL MATCH
    // =========================================================================
    private void nobarResultRoom(String roomCode) {
        page("Hasil Match Nobar — Room " + roomCode);

        getDb().collection("rooms").document(roomCode).collection("votes").get().addOnSuccessListener(votesSnap -> {
            int totalParticipants = votesSnap.size();
            if (totalParticipants == 0) totalParticipants = 1;

            Map<Integer, Integer> movieLikesCount = new HashMap<>();

            for (DocumentSnapshot vDoc : votesSnap.getDocuments()) {
                Map<String, Object> mvMap = (Map<String, Object>) vDoc.get("movieVotes");
                if (mvMap != null) {
                    for (Map.Entry<String, Object> entry : mvMap.entrySet()) {
                        int mId = Integer.parseInt(entry.getKey());
                        boolean liked = Boolean.TRUE.equals(entry.getValue());
                        if (liked) {
                            movieLikesCount.put(mId, movieLikesCount.getOrDefault(mId, 0) + 1);
                        }
                    }
                }
            }

            // Temukan Film dengan Match 100% dan Peringkat Suara Terbanyak
            List<MovieMatchResult> results = new ArrayList<>();
            for (Movie m : movies) {
                int likes = movieLikesCount.getOrDefault(m.getId(), 0);
                int pct = (likes * 100) / totalParticipants;
                results.add(new MovieMatchResult(m, likes, pct));
            }

            Collections.sort(results, (a, b) -> Integer.compare(b.matchPercentage, a.matchPercentage));

            boolean hasPerfectMatch = false;
            for (MovieMatchResult r : results) {
                if (r.matchPercentage == 100) {
                    hasPerfectMatch = true;
                    break;
                }
            }

            if (hasPerfectMatch) {
                label("🎉 HASIL KECOCOKAN 100%! Seluruh peserta menyukai film ini:");
            } else {
                label("💡 Belum ada film kecocokan 100%. Berikut film rekomendasi dengan suara terbanyak:");
            }

            for (MovieMatchResult r : results) {
                if (r.likesCount == 0 && !hasPerfectMatch) continue;

                Movie m = r.movie;

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(Color.parseColor("#1E293B"));
                int pad = (int) (14 * getResources().getDisplayMetrics().density);
                card.setPadding(pad, pad, pad, pad);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 10, 0, 10);
                card.setLayoutParams(lp);

                TextView tvBadge = new TextView(this);
                tvBadge.setText(r.matchPercentage == 100 ? "🏆 MATCH 100% (SEMUA PESERTA SUKA!)" : "⭐ KECOCOKAN " + r.matchPercentage + "% (" + r.likesCount + " Suara)");
                tvBadge.setTextColor(r.matchPercentage == 100 ? Color.GREEN : Color.parseColor("#38BDF8"));
                tvBadge.setTextSize(14);
                tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
                card.addView(tvBadge);

                TextView tvTitle = new TextView(this);
                tvTitle.setText(m.getTitle() + " (" + m.getYear() + ")");
                tvTitle.setTextColor(Color.WHITE);
                tvTitle.setTextSize(16);
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                tvTitle.setPadding(0, 4, 0, 4);
                card.addView(tvTitle);

                TextView tvSub = new TextView(this);
                tvSub.setText(m.getGenre() + " • " + durations.getOrDefault(m.getTitle(), 120) + " Menit");
                tvSub.setTextColor(Color.LTGRAY);
                tvSub.setTextSize(13);
                card.addView(tvSub);

                LinearLayout rowBtns = new LinearLayout(this);
                rowBtns.setOrientation(LinearLayout.HORIZONTAL);
                rowBtns.setPadding(0, 8, 0, 0);

                MaterialButton btnWatch = new MaterialButton(this);
                btnWatch.setText("🎬 Layar Nobar");
                btnWatch.setAllCaps(false);
                btnWatch.setOnClickListener(v -> showNobarScreen(m));
                rowBtns.addView(btnWatch);

                MaterialButton btnDetail = new MaterialButton(this);
                btnDetail.setText("📄 Detail Film");
                btnDetail.setAllCaps(false);
                btnDetail.setOnClickListener(v -> detail(m));
                rowBtns.addView(btnDetail);

                card.addView(rowBtns);
                body.addView(card);

                if (hasPerfectMatch && r.matchPercentage < 100) {
                    break;
                }
            }

            button("🔄 Ulangi Voting di Room Ini", () -> {
                getDb().collection("rooms").document(roomCode).update("status", "LOBBY");
                getDb().collection("rooms").document(roomCode).collection("participants").get().addOnSuccessListener(pSnap -> {
                    for (DocumentSnapshot pDoc : pSnap.getDocuments()) {
                        pDoc.getReference().update("votingFinished", false, "isReady", false);
                    }
                    nobarLobbyRoom(roomCode);
                });
            });

            button("🏠 Kembali ke Menu Utama Nobar", this::nobar);
        });
    }

    private static class MovieMatchResult {
        Movie movie;
        int likesCount;
        int matchPercentage;

        MovieMatchResult(Movie movie, int likesCount, int matchPercentage) {
            this.movie = movie;
            this.likesCount = likesCount;
            this.matchPercentage = matchPercentage;
        }
    }

    // =========================================================================
    // LAYAR PEMUTAR FILM & LAINNYA
    // =========================================================================
    private void showNobarScreen(Movie m) {
        String old = prefs.getString("history", "");
        String record = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()) + " | " + (currentRoomCode != null ? currentRoomCode : "LOKAL") + " | " + m.getTitle();
        if (!old.contains(record)) {
            prefs.edit().putString("history", record + "\n" + old).apply();
        }
        page("Layar Nobar — " + m.getTitle());

        LinearLayout screenFrame = new LinearLayout(this);
        screenFrame.setOrientation(LinearLayout.VERTICAL);
        screenFrame.setBackgroundColor(Color.parseColor("#121212"));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        screenFrame.setPadding(pad, pad, pad, pad);

        TextView tvBadge = new TextView(this);
        tvBadge.setText("🔴 LIVE STREAMING NOBAR • ROOM " + (currentRoomCode != null ? currentRoomCode : "LOKAL"));
        tvBadge.setTextColor(Color.parseColor("#FF5252"));
        tvBadge.setTextSize(14);
        tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        tvBadge.setPadding(0, 0, 0, 12);
        screenFrame.addView(tvBadge);

        ImageView poster = new ImageView(this);
        int resId = getResources().getIdentifier(m.getPoster(), "drawable", getPackageName());
        if (resId == 0) resId = R.drawable.poster_action;
        poster.setImageResource(resId);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (220 * getResources().getDisplayMetrics().density));
        poster.setLayoutParams(lp);
        poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
        screenFrame.addView(poster);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(m.getTitle() + " (" + m.getYear() + ")");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, 12, 0, 4);
        screenFrame.addView(tvTitle);

        TextView tvSub = new TextView(this);
        int dur = durations.getOrDefault(m.getTitle(), 120);
        tvSub.setText(m.getGenre() + " • " + dur + " Menit");
        tvSub.setTextColor(Color.LTGRAY);
        tvSub.setTextSize(14);
        tvSub.setPadding(0, 0, 0, 12);
        screenFrame.addView(tvSub);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(dur);
        pb.setProgress(dur / 3);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E50914")));
        screenFrame.addView(pb);

        isPlaying = true;
        TextView tvStatus = new TextView(this);
        tvStatus.setText("► Sedang memutar film bersama peserta Nobar");
        tvStatus.setTextColor(Color.GREEN);
        tvStatus.setTextSize(13);
        tvStatus.setPadding(0, 8, 0, 12);
        screenFrame.addView(tvStatus);

        body.addView(screenFrame);

        button("▶ / ❚❚ Play / Pause Layar", () -> {
            isPlaying = !isPlaying;
            if (isPlaying) {
                tvStatus.setText("► Sedang memutar film bersama peserta Nobar");
                tvStatus.setTextColor(Color.GREEN);
                Toast.makeText(this, "Layar Nobar: Memutar film", Toast.LENGTH_SHORT).show();
            } else {
                tvStatus.setText("❚❚ Layar Nobar Dijeda (Paused)");
                tvStatus.setTextColor(Color.YELLOW);
                Toast.makeText(this, "Layar Nobar: Dijeda", Toast.LENGTH_SHORT).show();
            }
        });

        button("🎬 Cari Platform Pemutar Resmi", () -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Tonton di Platform Resmi")
                    .setMessage("Pilih platform streaming resmi untuk menyaksikan " + m.getTitle())
                    .setItems(new String[]{"Netflix", "Prime Video", "Disney+"}, (d, which) -> {
                        String[] urls = {"https://www.netflix.com/search?q=" + Uri.encode(m.getTitle()), "https://www.primevideo.com/", "https://www.disneyplus.com/"};
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urls[which])));
                        } catch (Exception e) {
                            Toast.makeText(this, "Browser tidak tersedia", Toast.LENGTH_SHORT).show();
                        }
                    }).show();
        });

        button("📄 Detail Lengkap Film", () -> detail(m));
        button("🏠 Kembali ke Hasil Nobar", () -> {
            if (currentRoomCode != null) {
                nobarResultRoom(currentRoomCode);
            } else {
                nobar();
            }
        });
    }

    private void history() {
        page("Riwayat Nobar Match");
        String h = prefs.getString("history", "");
        label(h.isEmpty() ? "Belum ada riwayat film yang dipilih bersama." : h);
    }

    private int limit() { return prefs.getBoolean("plus", false) ? 50 : 5; }
    private String day() { return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    private int used() { return prefs.getInt("ai_" + day(), 0); }

    private void plus() {
        page("Paket Plus — prototipe");
        label("Gratis: 5 permintaan AI/hari\nPlus demo: 50 permintaan AI/hari\nTidak ada pembayaran atau iklan aktif pada prototipe ini.");
        label("Status: " + (prefs.getBoolean("plus", false) ? "Plus demo" : "Gratis"));
        button("Ubah paket demo", () -> {
            prefs.edit().putBoolean("plus", !prefs.getBoolean("plus", false)).apply();
            plus();
        });
    }

    private void ai() {
        // Layout Root Room Percakapan AI
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg_dark));
        mainLayout.setFitsSystemWindows(true);

        int density = (int) getResources().getDisplayMetrics().density;
        int p = 16 * density;

        // Top Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(p, p, p, 12 * density);
        header.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton btnBack = new MaterialButton(this);
        btnBack.setText("← Kembali");
        btnBack.setAllCaps(false);
        btnBack.setCornerRadius(12 * density);
        btnBack.setIconResource(R.drawable.ic_arrow_back);
        btnBack.setOnClickListener(v -> finish());
        header.addView(btnBack);

        LinearLayout headerTitles = new LinearLayout(this);
        headerTitles.setOrientation(LinearLayout.VERTICAL);
        headerTitles.setPadding(12 * density, 0, 0, 0);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        headerTitles.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("💬 Room Percakapan AI");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(17);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        headerTitles.addView(tvTitle);

        TextView tvQuota = new TextView(this);
        tvQuota.setText("Kuota hari ini: " + used() + " / " + limit());
        tvQuota.setTextColor(Color.LTGRAY);
        tvQuota.setTextSize(12);
        headerTitles.addView(tvQuota);

        header.addView(headerTitles);
        mainLayout.addView(header);

        // Scroll Area Percakapan
        ScrollView chatScroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        chatScroll.setLayoutParams(scrollParams);

        LinearLayout chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(p, 8 * density, p, 8 * density);
        chatScroll.addView(chatContainer);
        mainLayout.addView(chatScroll);

        // Pesan Selamat Datang AI
        addChatBubble(chatContainer, "🤖 Smart AI Assistant", "Halo! Saya Asisten AI MovieTracker. Tanyakan rekomendasi film apa saja, atau pilih topik cepat di bawah ini!", false);

        // Suggestion Chips (Rekomendasi Cepat)
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipContainer = new LinearLayout(this);
        chipContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipContainer.setPadding(p, 4 * density, p, 8 * density);

        String[] suggestions = {"🎭 Film Komedi Lucu", "❤️ Romantis Ending Bahagia", "👻 Horror Terseram", "🚀 Sci-Fi Plot Twist"};

        for (String s : suggestions) {
            MaterialButton chip = new MaterialButton(this);
            chip.setText(s);
            chip.setAllCaps(false);
            chip.setTextSize(12);
            chip.setTextColor(Color.parseColor("#38BDF8"));
            chip.setCornerRadius(16 * density);
            chip.setOnClickListener(v -> sendChatMessage(s, chatContainer, chatScroll, tvQuota));
            chipContainer.addView(chip);
        }
        chipScroll.addView(chipContainer);
        mainLayout.addView(chipScroll);

        // Bottom Input Area
        LinearLayout inputArea = new LinearLayout(this);
        inputArea.setOrientation(LinearLayout.HORIZONTAL);
        inputArea.setPadding(p, 8 * density, p, p);
        inputArea.setGravity(Gravity.CENTER_VERTICAL);
        inputArea.setBackgroundColor(Color.parseColor("#1E293B"));

        EditText inputField = new EditText(this);
        inputField.setHint("Ketik pesan / rekomendasi film...");
        inputField.setTextColor(Color.WHITE);
        inputField.setHintTextColor(Color.LTGRAY);
        inputField.setBackground(null);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        inputField.setLayoutParams(inputParams);
        inputArea.addView(inputField);

        MaterialButton btnSend = new MaterialButton(this);
        btnSend.setText("Kirim");
        btnSend.setAllCaps(false);
        btnSend.setCornerRadius(12 * density);
        btnSend.setIconResource(R.drawable.ui_spark);
        btnSend.setOnClickListener(v -> {
            String prompt = inputField.getText().toString().trim();
            if (!prompt.isEmpty()) {
                inputField.setText("");
                sendChatMessage(prompt, chatContainer, chatScroll, tvQuota);
            }
        });
        inputArea.addView(btnSend);

        mainLayout.addView(inputArea);
        setContentView(mainLayout);
    }

    private void addChatBubble(LinearLayout container, String sender, String text, boolean isUser) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (12 * getResources().getDisplayMetrics().density);
        card.setPadding(p, p, p, p);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 8, 0, 8);
        card.setLayoutParams(lp);

        if (isUser) {
            card.setBackgroundColor(Color.parseColor("#1E293B"));
        } else {
            card.setBackgroundColor(Color.parseColor("#1F1F2E"));
        }

        TextView tvSender = new TextView(this);
        tvSender.setText(sender);
        tvSender.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSender.setTextSize(13);
        tvSender.setTextColor(isUser ? Color.parseColor("#38BDF8") : Color.parseColor("#A78BFA"));
        card.addView(tvSender);

        TextView tvText = new TextView(this);
        tvText.setText(text);
        tvText.setTextSize(15);
        tvText.setTextColor(Color.WHITE);
        tvText.setPadding(0, 4, 0, 0);
        card.addView(tvText);

        container.addView(card);
    }

    private String generateLocalAiResponse(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        List<Movie> matched = new ArrayList<>();

        if (lower.contains("komedi") || lower.contains("lucu") || lower.contains("tertawa") || lower.contains("superbad") || lower.contains("hangover") || lower.contains("home alone")) {
            sb.append("Berikut rekomendasi film Komedi paling seru dan mengocok perut:\n\n");
            for (Movie m : movies) {
                if ("Comedy".equalsIgnoreCase(m.getGenre())) matched.add(m);
            }
        } else if (lower.contains("romantis") || lower.contains("cinta") || lower.contains("romance") || lower.contains("titanic") || lower.contains("notebook") || lower.contains("la la land")) {
            sb.append("Berikut rekomendasi film Romantis penuh kisah manis dan emosional:\n\n");
            for (Movie m : movies) {
                if ("Romance".equalsIgnoreCase(m.getGenre())) matched.add(m);
            }
        } else if (lower.contains("horror") || lower.contains("horor") || lower.contains("seram") || lower.contains("takut") || lower.contains("conjuring") || lower.contains("get out") || lower.contains("quiet place")) {
            sb.append("Berikut rekomendasi film Horror terseram pilihan AI:\n\n");
            for (Movie m : movies) {
                if ("Horror".equalsIgnoreCase(m.getGenre())) matched.add(m);
            }
        } else if (lower.contains("sci-fi") || lower.contains("scifi") || lower.contains("masa depan") || lower.contains("inception") || lower.contains("interstellar") || lower.contains("matrix")) {
            sb.append("Berikut rekomendasi film Sci-Fi spektakuler dengan ide cerita luar biasa:\n\n");
            for (Movie m : movies) {
                if ("Sci-Fi".equalsIgnoreCase(m.getGenre())) matched.add(m);
            }
        } else if (lower.contains("action") || lower.contains("aksi") || lower.contains("seru") || lower.contains("john wick") || lower.contains("mad max") || lower.contains("dark knight")) {
            sb.append("Berikut rekomendasi film Action pilihan dengan pertarungan sengit:\n\n");
            for (Movie m : movies) {
                if ("Action".equalsIgnoreCase(m.getGenre())) matched.add(m);
            }
        }

        if (matched.isEmpty()) {
            sb.append("Berikut 3 rekomendasi film terbaik pilihan AI dari katalog lokal:\n\n");
            List<Movie> copy = new ArrayList<>(movies);
            Collections.shuffle(copy);
            for (int i = 0; i < Math.min(3, copy.size()); i++) {
                matched.add(copy.get(i));
            }
        }

        for (Movie m : matched) {
            int dur = durations.getOrDefault(m.getTitle(), 120);
            sb.append("🎬 ").append(m.getTitle()).append(" (").append(m.getYear()).append(")\n");
            sb.append("   Genre: ").append(m.getGenre()).append(" • ").append(dur).append(" Menit\n");
            sb.append("   Sinopsis: ").append(m.getSynopsis()).append("\n\n");
        }

        sb.append("💡 Rekomendasi di atas dibuat secara otomatis oleh Smart AI Assistant berdasarkan topik yang kamu tanyakan!");
        return sb.toString();
    }

    private void sendChatMessage(String prompt, LinearLayout chatContainer, ScrollView chatScroll, TextView tvQuota) {
        if (busy) return;
        if (used() >= limit()) {
            addChatBubble(chatContainer, "⚠️ Info Kuota", "Kuota harian habis (" + used() + "/" + limit() + "). Silakan coba lagi besok atau aktifkan Paket Plus.", false);
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
            return;
        }

        busy = true;
        // Tambahkan bubble user
        addChatBubble(chatContainer, "Anda", prompt, true);

        // Tambahkan bubble loading
        LinearLayout loadingCard = new LinearLayout(this);
        loadingCard.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (12 * getResources().getDisplayMetrics().density);
        loadingCard.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        loadingCard.setLayoutParams(lp);
        loadingCard.setBackgroundColor(Color.parseColor("#1F1F2E"));

        TextView tvLoad = new TextView(this);
        tvLoad.setText("🤖 Smart AI sedang mengetik...");
        tvLoad.setTextColor(Color.LTGRAY);
        tvLoad.setTextSize(14);
        loadingCard.addView(tvLoad);
        chatContainer.addView(loadingCard);

        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));

        new Thread(() -> {
            try {
                Thread.sleep(400); // Simulasi singkat pemrosesan AI
            } catch (Exception ignored) {}

            String answer = generateLocalAiResponse(prompt);

            runOnUiThread(() -> {
                prefs.edit().putInt("ai_" + day(), used() + 1).apply();
                busy = false;
                chatContainer.removeView(loadingCard);
                addChatBubble(chatContainer, "🤖 Smart AI Assistant", answer, false);
                tvQuota.setText("Kuota hari ini: " + used() + " / " + limit());
                chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
            });
        }).start();
    }
}
