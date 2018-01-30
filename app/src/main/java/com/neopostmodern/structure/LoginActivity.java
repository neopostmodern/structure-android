package com.neopostmodern.structure;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

public class LoginActivity extends AppCompatActivity {
    private StructureApplication application;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        application = (StructureApplication) getApplication();

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.goBack();
        webView.setWebViewClient(new AuthWebviewClient(this)); // if user clicks on a url we need to steal that click, also steal the back button
        webView.loadUrl(StructureApplication.SERVER_URL + "/login/github");
        setContentView(webView);
    }

    public void loginComplete(String cookie) {
        application.registerCookie(cookie);

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }
}
