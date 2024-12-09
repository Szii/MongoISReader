/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mongoisreader.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 *
 * @author brune
 */
@Service
public class IdFetcherService {
    
    private static final Logger logger = LoggerFactory.getLogger(IdFetcherService.class);

    private static final OkHttpClient HTTP_CLIENT = createUnsafeOkHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private static final int MAX_FETCH_ATTEMPTS = 3;
    private static final int INITIAL_DELAY_MILLIS = 2000;
    
    private static final ConcurrentMap<Integer, String> PDF_ID_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, String> DOCX_ID_CACHE = new ConcurrentHashMap<>();
    
    
    public IdFetcherService(){
        
    }
    
    
    public String fetchIdWithRetry(Integer zneniDokumentId, String format) {
        String cachedId = format.equals("PDF") ? PDF_ID_CACHE.get(zneniDokumentId) : DOCX_ID_CACHE.get(zneniDokumentId);
        if (cachedId != null) {
            return cachedId;
        }

        int attempt = 0;
        int delayMillis = INITIAL_DELAY_MILLIS;

        while (attempt < MAX_FETCH_ATTEMPTS) {
            attempt++;
            String id = fetchDocumentId(zneniDokumentId, format);
            if (id != null) {
                return id;
            }
            logger.info("Attempt {}/{}: Waiting {} ms before retrying for znění-dokument-id {} format {}.",
                    attempt, MAX_FETCH_ATTEMPTS, delayMillis, zneniDokumentId, format);
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while waiting to retry fetching ID: {}", e.getMessage());
                break;
            }
            delayMillis *= 1; // Exponential backoff
        }

        logger.warn("Exceeded max attempts for fetching ID for znění-dokument-id {} format {}.", zneniDokumentId, format);
        return null;
    }
    
    
    
    private String fetchDocumentId(Integer zneniDokumentId, String format) {
        String apiUrl = String.format("https://www.e-sbirka.cz/sbr-cache/stahni/informativni-zneni/%d/%s", zneniDokumentId, format);
        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "curl/7.68.0")
                .header("Connection", "keep-alive")
                .build();
    
        System.out.println(apiUrl);
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (response.isSuccessful()) {
                JsonNode responseJson = OBJECT_MAPPER.readTree(responseBody);
                JsonNode idNode = responseJson.get("id");
                if (idNode != null && !idNode.isNull()) {
                    return idNode.asText();
                } else {
                    logger.info("Document generation in progress for znění-dokument-id {} format {}.", zneniDokumentId, format);
                    return null;
                }
            } else {
                logger.warn("Failed to fetch document ID for znění-dokument-id {} format {}. Status Code: {}. Response: {}",
                        zneniDokumentId, format, response.code(), responseBody);
                return null;
            }
        } catch (IOException e) {
            logger.error("IOException while fetching document ID for znění-dokument-id {} format {}: {}",
                    zneniDokumentId, format, e.getMessage());
            return null;
        }
    }
        
        
        
    private static OkHttpClient createUnsafeOkHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509ExtendedTrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] xcs, String string, Socket socket) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] xcs, String string, Socket socket) throws CertificateException {
                }

                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] xcs, String string, SSLEngine ssle) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] xcs, String string, SSLEngine ssle) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] xcs, String string) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] xcs, String string) throws CertificateException {
                }
            }};

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .retryOnConnectionFailure(true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
