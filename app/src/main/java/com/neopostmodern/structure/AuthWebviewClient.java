package com.neopostmodern.structure;

import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class AuthWebviewClient extends WebViewClient {
    private LoginActivity loginActivity;

    AuthWebviewClient(LoginActivity activity) {
        super();

        this.loginActivity = activity;
    }

    @Override
    public void onPageFinished(WebView view, String url){
        String cookies = CookieManager.getInstance().getCookie(url);
        Log.d("AuthWebViewClient", "Finished loading: " + url);
        Log.d("AuthWebViewClient", "Cookies: " + cookies);

        if (url.contains("success")) {
            loginActivity.loginComplete(cookies);
        }
    }
}
