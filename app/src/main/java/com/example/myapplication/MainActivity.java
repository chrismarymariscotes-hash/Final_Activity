package com.example.myapplication;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private EditText etName, etNote;
    private Button btnSave;
    private ListView listView;
    private TextView tvStatus;
    private DatabaseReference dbRef;

    private final List<String> rows = new ArrayList<>();
    private final List<String> keys = new ArrayList<>(); // Firebase key for each row
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etName = findViewById(R.id.etName);
        etNote = findViewById(R.id.etNote);
        btnSave = findViewById(R.id.btnSave);
        Button btnLoad = findViewById(R.id.btnLoad);
        listView = findViewById(R.id.listView);
        tvStatus = findViewById(R.id.tvStatus);

        dbRef = FirebaseDatabase.getInstance().getReference("entries");
        // If your database is not in the US region, use:
        // FirebaseDatabase.getInstance("https://YOUR-DB-URL").getReference("entries");

        adapter = new ArrayAdapter<>(this, R.layout.item_entry, R.id.tvRow, rows);
        listView.setAdapter(adapter);

        btnSave.setOnClickListener(v -> saveEntry());
        btnLoad.setOnClickListener(v -> loadEntries());

        // Tap a row: show the full entry
        listView.setOnItemClickListener((parent, view, position, id) ->
                new AlertDialog.Builder(this)
                        .setTitle("Entry " + (position + 1))
                        .setMessage(rows.get(position))
                        .setPositiveButton("OK", null)
                        .show());

        // Long-press a row: delete it after confirmation
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete entry?")
                    .setMessage(rows.get(position))
                    .setPositiveButton("Delete", (d, w) ->
                            dbRef.child(keys.get(position)).removeValue()
                                    .addOnSuccessListener(u -> {
                                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                                        loadEntries();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this, "Delete failed: " + e.getMessage(),
                                                    Toast.LENGTH_LONG).show()))
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
    }

    private void saveEntry() {
        String name = etName.getText().toString().trim();
        String note = etNote.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError("Required");
            return;
        }
        if (note.isEmpty()) {
            etNote.setError("Required");
            return;
        }

        Map<String, Object> entry = new HashMap<>();
        entry.put("name", name);
        entry.put("note", note);
        entry.put("timestamp", System.currentTimeMillis());

        btnSave.setEnabled(false); // prevent double submit
        btnSave.setText("Saving...");

        dbRef.push().setValue(entry)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Saved successfully", Toast.LENGTH_SHORT).show();
                    etName.setText("");
                    etNote.setText("");
                    etName.requestFocus();
                    resetSaveButton();
                    loadEntries(); // refresh so the new record shows up
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Save failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    resetSaveButton();
                });
    }

    private void resetSaveButton() {
        btnSave.setEnabled(true);
        btnSave.setText("Save");
    }

    private void loadEntries() {
        tvStatus.setText("Loading...");
        dbRef.get().addOnCompleteListener(task -> {
            rows.clear();
            keys.clear();

            if (!task.isSuccessful()) {
                adapter.notifyDataSetChanged();
                tvStatus.setText("Failed to load");
                Toast.makeText(this, "Failed to load entries", Toast.LENGTH_LONG).show();
                return;
            }

            DataSnapshot snapshot = task.getResult();
            if (snapshot == null || !snapshot.exists()) {
                adapter.notifyDataSetChanged();
                tvStatus.setText("No entries yet");
                Toast.makeText(this, "No entries found", Toast.LENGTH_SHORT).show();
                return;
            }

            // Push keys are time-ordered, so rows appear in the order saved
            for (DataSnapshot child : snapshot.getChildren()) {
                String name = child.child("name").getValue(String.class);
                String note = child.child("note").getValue(String.class);
                rows.add(name + " - " + note);
                keys.add(child.getKey());
            }

            adapter.notifyDataSetChanged();
            tvStatus.setText(rows.size() + (rows.size() == 1 ? " entry loaded" : " entries loaded"));
        });
    }
}