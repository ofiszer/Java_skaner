package com.example.java_skaner;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;

import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.util.function.Consumer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import android.media.MediaScannerConnection;

public class MainActivity extends AppCompatActivity {

    private Button readBtn;
    private Button saveTxtButton, saveHtmlButton, savePdfButton;

    private EditText input;
    private TextView text;
    private final ArrayList<Uri> imageUris = new ArrayList<>();
    private ActivityResultLauncher<IntentSenderRequest> scannerLauncher;
    private ActivityMainBinding binding;

    private boolean isValidFileName(String fileName) {
        return !fileName.isEmpty() && fileName.matches("^[^\\\\/:*?\"<>|]+$");
    }

    private static final int READ_REQUEST_CODE = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // assign views
        readBtn = findViewById(R.id.read);
        input = findViewById(R.id.input);
        text = findViewById(R.id.text);

        // two buttons for txt and html save
        saveTxtButton = new Button(this);
        saveTxtButton.setText("Zapisz jako TXT");
        saveTxtButton.setVisibility(View.GONE);
        saveTxtButton.setOnClickListener(v -> {
            String content = input.getText().toString();
            showSaveFileDialog(".txt", fileName -> writeToFile(fileName, content));
        });

        saveHtmlButton = new Button(this);
        saveHtmlButton.setText("Zapisz jako HTML");
        saveHtmlButton.setVisibility(View.GONE);
        saveHtmlButton.setOnClickListener(v -> {
            String content = input.getText().toString();
            String html = "<html><body><pre style='color: black; font-size: 16px;'>" +
                    content +
                    "</pre></body></html>";
            showSaveFileDialog(".html", fileName->writeToFile(fileName, html));
        });

        savePdfButton = new Button(this);
        savePdfButton.setText("Zapisz jako PDF");
        savePdfButton.setVisibility(View.GONE);
        savePdfButton.setOnClickListener(v -> {
            String content = input.getText().toString();
            showSaveFileDialog(".pdf", fileName -> writeToPdfFile(fileName, content));
        });


        Button saveChangesBtn = new Button(this);
        saveChangesBtn.setText("Zapisz zmiany");
        saveChangesBtn.setOnClickListener(v->{
            if (currentFileUri != null){
                String editedContent = input.getText().toString();
                writeTextToUri(currentFileUri, editedContent);
            } else {
                Toast.makeText(this, "Nie wybrano pliku do edycji.", Toast.LENGTH_SHORT).show();
            }
        });

        binding.mainContainer.addView(saveTxtButton);
        binding.mainContainer.addView(saveHtmlButton);
        binding.mainContainer.addView(savePdfButton);
        binding.mainContainer.addView(saveChangesBtn);

        // read button
        readBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            String[] mimeTypes = {"text/plain", "text/html", "application/pdf"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            startActivityForResult(intent, READ_REQUEST_CODE);
        });

        // clear button
        Button clearButton = new Button(this);
        clearButton.setText("Wyczyść dokument");
        clearButton.setOnClickListener(v -> {
            input.setText("");
            text.setText("");
        });
        binding.mainContainer.addView(clearButton);

        // open files from phone
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes = {"text/plain", "text/html", "application/pdf"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, READ_REQUEST_CODE);

        // nav configuration
        BottomNavigationView navView = findViewById(R.id.nav_view);
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // scanner configuration
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
                                //savePdfToFile(pdfUri);
                            }

                            Toast.makeText(this, "Zeskanowano " + imageUris.size() + " stron(y)", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

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

    private Uri currentFileUri = null;

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == READ_REQUEST_CODE && resultCode == RESULT_OK && data != null){
            currentFileUri = data.getData();

            String type = getContentResolver().getType(currentFileUri);
            if (type == null || (!type.equals("text/plain") && !type.equals("text/html") && !type.equals("application/pdf"))){
                String path = currentFileUri.getPath();
                if (path != null) {
                    path = path.toLowerCase();
                    if (path.endsWith(".pdf")) {
                        type = "application/pdf";
                    } else if (path.endsWith(".html") || path.endsWith(".htm")) {
                        type = "text/html";
                    } else if (path.endsWith(".txt")) {
                        type = "text/plain";
                    }
                }
            }
            if ("text/plain".equals(type) || "text/html".equals(type)) {
                String content = readTextFromUri(currentFileUri);
                input.setVisibility(View.VISIBLE);
                input.setText(content);

                ImageView imageView = findViewById(R.id.pdfView);
                imageView.setVisibility(View.GONE);
            } else if ("application/pdf".equals(type)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                    try{
                        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(currentFileUri, "r");
                        PdfRenderer renderer = new PdfRenderer(pfd);
                        PdfRenderer.Page page = renderer.openPage(0);

                        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                        ImageView imageView = findViewById(R.id.pdfView);
                        imageView.setImageBitmap(bitmap);
                        imageView.setVisibility(View.VISIBLE);

                        input.setVisibility(View.GONE);

                        page.close();
                        renderer.close();
                        pfd.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Błąd podczas otwierania pliku PDF", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
             Toast.makeText(this, "Nieobsługiwany typ pliku", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void writeTextToUri(Uri uri, String content){
        try{
            OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt");
            if (outputStream != null){
                outputStream.write(content.getBytes());
                outputStream.close();
                Toast.makeText(this, "Zapisano zmiany.", Toast.LENGTH_SHORT).show();
            }
        }catch(Exception e){
            e.printStackTrace();
            Toast.makeText(this, "Błąd edycji: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    // read from txt and html files
    private String readTextFromUri(Uri uri){
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))){
            String line;
            while ((line=reader.readLine())!=null){
                builder.append(line).append("\n");
            }

            //TextView textView = findViewById(R.id.text);
            //textView.setText(builder.toString());
        } catch (IOException e) {
            e.printStackTrace();
            return "Błąd odczytu: " + e.getMessage();
            //Toast.makeText(this, "Błąd odczytu: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return builder.toString();
    }


    // choosing file name
    private void showSaveFileDialog(String extension, Consumer<String> onFileNameEntered){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Wpisz nazwę pliku: ");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Nazwa pliku bez rozszerzenia");
        builder.setView(input);

        // zapis pliku z walidacją nazwy
        builder.setPositiveButton("Zapisz", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(view -> {
                String fileName = input.getText().toString().trim();
                if (fileName.isEmpty()) {
                    input.setError("Nazwa pliku nie może być pusta!");
                    return;
                }

                if (!isValidFileName(fileName)) {
                    input.setError("Nazwa zawiera niedozwolone znaki: \\ / : * ? \" < > |");
                    return;
                }

                if (!fileName.endsWith(extension)) {
                    fileName += extension;
                }

                dialog.dismiss();
                onFileNameEntered.accept(fileName);
            });
        });

        dialog.show();

        //builder.setNegativeButton("Anuluj", (dialog, which)->dialog.cancel());
        //builder.show();
    }


    // showing recognized text
    private void recognizeTextFromImage(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image).addOnSuccessListener(visionText -> {
                String resultText = visionText.getText();
                input.setText(resultText);  // save text into edittext

                saveTxtButton.setVisibility(View.VISIBLE);
                saveHtmlButton.setVisibility(View.VISIBLE);
                savePdfButton.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Wykryto tekst:\n" + resultText, Toast.LENGTH_LONG).show();

                try {
                    /*File resultFile = new File(getFilesDir(), "recognized_text.txt");
                    FileWriter writer = new FileWriter(resultFile);
                    writer.write(resultText);
                    writer.close();*/

                    // Don't save text yet - show it in the editor
                    input.setText(resultText);

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

    // save as pdf
    private void writeToPdfFile(String fileName, String content){
        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842,1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        paint.setColor(Color.BLACK);
        paint.setTextSize(12f);

        int x = 10;
        int y = 25;
        for (String line: content.split("\n")){
            canvas.drawText(line, x, y, paint);
            y += paint.descent() - paint.ascent();
        }
        pdfDocument.finishPage(page);

        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(path, fileName);

        try{
            if (!path.exists()){
                path.mkdirs();
            }

        FileOutputStream fos = new FileOutputStream(file);
        pdfDocument.writeTo(fos);
        pdfDocument.close();
        fos.close();

        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
        Toast.makeText(this, "Zapisano PDF: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();

        }catch(IOException e){
            e.printStackTrace();
            Toast.makeText(this, "Błąd zapisu PDF: "+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // PDF
    /*private void savePdfToFile(Uri pdfUri) {
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
    }*/

    // save to the txt/html/pdf file
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