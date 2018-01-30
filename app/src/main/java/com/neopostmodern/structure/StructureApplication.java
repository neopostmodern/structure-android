package com.neopostmodern.structure;

import android.app.Application;
import android.util.Log;

import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.CustomTypeAdapter;
import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.ResponseField;
import com.apollographql.apollo.cache.normalized.CacheKey;
import com.apollographql.apollo.cache.normalized.CacheKeyResolver;
import com.apollographql.apollo.cache.normalized.NormalizedCacheFactory;
import com.apollographql.apollo.cache.normalized.lru.EvictionPolicy;
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory;
import com.apollographql.apollo.cache.normalized.sql.ApolloSqlHelper;
import com.apollographql.apollo.cache.normalized.sql.SqlNormalizedCacheFactory;
import com.neopostmodern.structure.apollo.type.CustomType;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import javax.annotation.Nonnull;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StructureApplication extends Application {
    static final String TAG = "StructureApplication";

    public static final String SERVER_URL = com.neopostmodern.structure.BuildConfig.SERVER_URL;
    private static final String BASE_URL = StructureApplication.SERVER_URL + "/graphql";
    private static final String SQL_CACHE_NAME = "structuredb";
    private ApolloClient apolloClient;
    private String authCookie = "";

    private String urlToAdd = null;

    @Override public void onCreate() {
        super.onCreate();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        final Request original = chain.request();

                        final Request authorized = original.newBuilder()
                                .addHeader("Cookie", authCookie)
                                .build();

                        return chain.proceed(authorized);
                    }
                })
                .build();

        ApolloSqlHelper apolloSqlHelper = new ApolloSqlHelper(this, SQL_CACHE_NAME);
        NormalizedCacheFactory normalizedCacheFactory = new LruNormalizedCacheFactory(EvictionPolicy.NO_EVICTION)
                .chain(new SqlNormalizedCacheFactory(apolloSqlHelper));

        CacheKeyResolver cacheKeyResolver = new CacheKeyResolver() {
            @Nonnull @Override
            public CacheKey fromFieldRecordSet(@Nonnull ResponseField field, @Nonnull Map<String, Object> recordSet) {
                String typeName = (String) recordSet.get("__typename");
                if ("User".equals(typeName)) {
                    String userKey = typeName + "." + recordSet.get("login");
                    return CacheKey.from(userKey);
                }
                if (recordSet.containsKey("id")) {
                    String typeNameAndIDKey = recordSet.get("__typename") + "." + recordSet.get("id");
                    return CacheKey.from(typeNameAndIDKey);
                }
                return CacheKey.NO_KEY;
            }

            // Use this resolver to customize the key for fields with variables: eg entry(repoFullName: $repoFullName).
            // This is useful if you want to make query to be able to resolved, even if it has never been run before.
            @Nonnull @Override
            public CacheKey fromFieldArguments(@Nonnull ResponseField field, @Nonnull Operation.Variables variables) {
                return CacheKey.NO_KEY;
            }
        };

        apolloClient = ApolloClient.builder()
                .serverUrl(BASE_URL)
                .okHttpClient(okHttpClient)
                .normalizedCache(normalizedCacheFactory, cacheKeyResolver)
                .addCustomTypeAdapter(CustomType.DATE, new CustomTypeAdapter<Date>() {
                    @Nonnull
                    @Override
                    public Date decode(@Nonnull String value) {
                        return new Date(Long.parseLong(value));
                    }

                    @Nonnull
                    @Override
                    public String encode(@Nonnull Date value) {
                        return String.valueOf(value.getTime());
                    }
                })
                .build();
    }

    public ApolloClient apolloClient() {
        return apolloClient;
    }

    public void registerCookie(String cookie) {
        authCookie = cookie;
    }
    public boolean isAuthenticated() {
        return this.authCookie.length() > 0;
    }
    public void queueUrlToAdd(String url) {
        urlToAdd = url;
    }
    public String popUrlToAdd() {
        String url = urlToAdd;
        urlToAdd = null;
        return url;
    }
}
