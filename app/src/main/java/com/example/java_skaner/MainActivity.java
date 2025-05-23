package com.example.java_skaner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import android.net.Uri;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.Button;
import android.net.Uri;

import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.java_skaner.databinding.ActivityMainBinding;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.googlecode.tesseract.android.TessBaseAPI;


import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<IntentSenderRequest> scannerLauncher;
    private final ArrayList<Uri> imageUris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setPageLimit(5)
                .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                ).build();

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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

                                // tutaj wywoływanie funkcji rozpoznającej tekst
                                recognizeTextFromImage(imageUri);
                            }
                            if (scanningResult.getPdf() != null) {
                                Uri pdfUri = scanningResult.getPdf().getUri();
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
                                    //e.printStackTrace();
                                    Log.e("DocumentScan", "Błąd zapisu PDF", e);
                                    Toast.makeText(this, "Błąd zapisu PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }

                            Toast.makeText(this, "Zeskanowano " + imageUris.size() + " stron(y)", Toast.LENGTH_SHORT).show();
                        }
                    }
                });


        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        Button scanButton = new Button(this);
        scanButton.setText("Skanuj dokument");

        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
        scanButton.setOnClickListener(v -> {
            scanner.getStartScanIntent(MainActivity.this).addOnSuccessListener(pendingIntent -> {
                IntentSenderRequest request   = new IntentSenderRequest.Builder(pendingIntent).build();
                scannerLauncher.launch(request);
            }).addOnFailureListener(e -> {
                Toast.makeText(MainActivity.this, "Błąd skanowania: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        });


        binding.mainContainer.addView(scanButton);
        //layout.addView(scanButton);
        //setContentView(layout); // zastępuje widok główny
    }
    // Zamiana zdjęcia na tekst

    private void recognizeTextFromImage(Uri imageUri) {
        try{
            InputImage image = InputImage.fromFilePath(this, imageUri);

            // do wykrycia łacińskich znaków
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image).addOnSuccessListener(visionText ->{
                String resultText = visionText.getText();
                Toast.makeText(this, "Wykryto tekst: \n" + resultText, Toast.LENGTH_LONG).show();
                // zapis do pliku
                try{
                    File resultFile = new File(getFilesDir(), "recognized_text.txt");
                    FileWriter writer = new FileWriter(resultFile);
                    writer.write(resultText);
                    writer.close();
                }catch(Exception e){
                    Log.e("OCR", "Błąd zapisu pliku", e);
                }
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Błąd OCR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }catch(Exception e){
            Toast.makeText(this, "Błąd wczytywania obrazu", Toast.LENGTH_SHORT).show();
        }
    }
}
