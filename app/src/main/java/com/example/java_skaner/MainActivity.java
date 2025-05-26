package com.example.java_skaner;

import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.java_skaner.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;

import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import android.media.MediaScannerConnection;

public class MainActivity extends AppCompatActivity {

    private Button writeBtn, readBtn;
    private EditText input;
    private TextView text;
    private final ArrayList<Uri> imageUris = new ArrayList<>();
    private ActivityResultLauncher<IntentSenderRequest> scannerLauncher;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Przypisz widoki
        writeBtn = findViewById(R.id.write);
        readBtn = findViewById(R.id.read);
        input = findViewById(R.id.input);
        text = findViewById(R.id.text);

        // Przycisk zapisu
        writeBtn.setOnClickListener(v -> {
            String content = input.getText().toString();
            Toast.makeText(this, "Zapisuję: " + content, Toast.LENGTH_SHORT).show();
            writeToFile("file.txt", content);
            String htmlContent = "<html><body><pre style='color: black; font-size: 16px;'>" +
                    content +
                    "</pre></body></html>";
            writeToFile("file.html", htmlContent);

        });

        // Przycisk odczytu
        readBtn.setOnClickListener(v -> {
            String content = readFromFile("file.txt");
            text.setText(content);
            Toast.makeText(this, "Odczytano plik.", Toast.LENGTH_SHORT).show();
        });

        // Konfiguracja nawigacji
        BottomNavigationView navView = findViewById(R.id.nav_view);
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // Konfiguracja skanera
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setPageLimit(5)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .build();

        scannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        GmsDocumentScanningResult scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
                        if (scanningResult != null && scanningResult.getPages() != null) {
                            imageUris.clear();
                            for (GmsDocumentScanningResult.Page page : scanningResult.getPages()) {
                                Uri imageUri = page.getImageUri();
                                imageUris.add(imageUri);
                                recognizeTextFromImage(imageUri);
                            }

                            if (scanningResult.getPdf() != null) {
                                Uri pdfUri = scanningResult.getPdf().getUri();
                                savePdfToFile(pdfUri);
                            }

                            Toast.makeText(this, "Zeskanowano " + imageUris.size() + " stron(y)", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        // Przycisk dynamicznie dodany
        Button scanButton = new Button(this);
        scanButton.setText("Skanuj dokument");

        scanButton.setOnClickListener(v -> {
            GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
            scanner.getStartScanIntent(MainActivity.this).addOnSuccessListener(pendingIntent -> {
                IntentSenderRequest request = new IntentSenderRequest.Builder(pendingIntent).build();
                scannerLauncher.launch(request);
            }).addOnFailureListener(e -> {
                Toast.makeText(MainActivity.this, "Błąd skanowania: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        });

        binding.mainContainer.addView(scanButton);
    }

    // Zapis rozpoznanego tekstu
    private void recognizeTextFromImage(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image).addOnSuccessListener(visionText -> {
                String resultText = visionText.getText();
                input.setText(resultText);  // zapis tekstu do edittext
                Toast.makeText(this, "Wykryto tekst:\n" + resultText, Toast.LENGTH_LONG).show();

                try {
                    File resultFile = new File(getFilesDir(), "recognized_text.txt");
                    FileWriter writer = new FileWriter(resultFile);
                    writer.write(resultText);
                    writer.close();
                } catch (Exception e) {
                    Log.e("OCR", "Błąd zapisu pliku", e);
                }
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Błąd OCR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });

        } catch (Exception e) {
            Toast.makeText(this, "Błąd wczytywania obrazu", Toast.LENGTH_SHORT).show();
        }
    }

    // Zapis PDF
    private void savePdfToFile(Uri pdfUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(pdfUri);
            if (inputStream != null) {
                File pdfFile = new File(getFilesDir(), "scan.pdf");
                FileOutputStream outputStream = new FileOutputStream(pdfFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.close();
                inputStream.close();
            }
        } catch (Exception e) {
            Log.e("PDF", "Błąd zapisu PDF", e);
            Toast.makeText(this, "Błąd zapisu PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Zapis tekstu
    public void writeToFile(String fileName, String content) {
        //File path = getApplicationContext().getFilesDir();
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(path, fileName);
        //FileOutputStream writer = new FileOutputStream(file);
        try {
            if (!path.exists()){
                path.mkdirs();
            }
            FileOutputStream writer = new FileOutputStream(file);
            writer.write(content.getBytes());
            writer.close();

            MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, (path1, uri)->Log.i("Zapis", "Plik zindeksowany: " + uri));

            Toast.makeText(getApplicationContext(), "Zapisano do pliku " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Błąd zapisu" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Odczyt tekstu
    public String readFromFile(String fileName) {
        File path = getApplicationContext().getFilesDir();
        File readFrom = new File(path, fileName);

        if (!readFrom.exists()){
            return "Plik nie istnieje.";
        }

        byte[] content = new byte[(int) readFrom.length()];
        try {
            FileInputStream stream = new FileInputStream(readFrom);
            stream.read(content);
            return new String(content);
        } catch (Exception e) {
            e.printStackTrace();
            return e.toString();
        }
    }
}