package com.keenresearch.keenasr_frontline_poc;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.webkit.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        setupUi();
    }

    private void setupUi(){
        Toolbar toolbar = findViewById(R.id.helpToolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.arrow_back_48px);
        toolbar.setNavigationOnClickListener(view -> finish());
        WebView myWebView = findViewById(R.id.helpWebView);
        myWebView.loadData(fetchHtmlFile(), "text/html", "UTF-8");
    }

    private String fetchHtmlFile(){
        // Get a reference to the AssetManager
        AssetManager assetManager = getAssets();
        // Read the contents of the HTML file into a string
        String htmlContent;
        try (InputStream inputStream = assetManager.open("help.html")) {
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            int bytesRead = inputStream.read(buffer);
            if (bytesRead > 0) {
                htmlContent = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            } else {
                htmlContent = "";
            }
        } catch (IOException e) {
            Log.e("HelpActivity", "Error reading help.html", e);
            htmlContent = "";
        }
        return htmlContent;
    }

}
