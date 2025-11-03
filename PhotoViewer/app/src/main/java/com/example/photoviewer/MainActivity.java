package com.example.photoviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView textView;
    String siteUrl = "http://127.0.0.1:8000/";
    String token = "";  // 인증이 필요한 경우 토큰을 여기에 입력

    CloadImage taskDownload;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
    }

    public void onClickDownload(View v) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(siteUrl + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();
    }

    public void onClickUpload(View v) {
        Toast.makeText(getApplicationContext(), "Upload", Toast.LENGTH_LONG).show();
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmapList = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                String apiUrl = urls[0];
                Log.d("PhotoViewer", "API URL: " + apiUrl);
                URL urlAPI = new URL(apiUrl);
                conn = (HttpURLConnection) urlAPI.openConnection();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Token " + token);
                }
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                Log.d("PhotoViewer", "Response Code: " + responseCode);
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    is.close();

                    String strJson = result.toString();
                    Log.d("PhotoViewer", "JSON Response: " + strJson.substring(0, Math.min(500, strJson.length())));
                    
                    JSONArray aryJson = new JSONArray(strJson);
                    Log.d("PhotoViewer", "Total posts: " + aryJson.length());
                    
                    for (int i = 0; i < aryJson.length(); i++) {
                        JSONObject post = aryJson.getJSONObject(i);
                        String imageUrl = post.optString("image", "");
                        Log.d("PhotoViewer", "Post " + i + " image URL: " + imageUrl);
                        
                        if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.equals("null")) {
                            // 상대 경로인 경우 절대 URL로 변환
                            if (imageUrl.startsWith("/")) {
                                imageUrl = siteUrl + imageUrl;
                            } else if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                                imageUrl = siteUrl + "/" + imageUrl;
                            }
                            
                            Log.d("PhotoViewer", "Downloading image from: " + imageUrl);
                            
                            try {
                                URL myImageUrl = new URL(imageUrl);
                                HttpURLConnection imgConn = (HttpURLConnection) myImageUrl.openConnection();
                                imgConn.setConnectTimeout(10000);
                                imgConn.setReadTimeout(10000);
                                int imgResponseCode = imgConn.getResponseCode();
                                
                                if (imgResponseCode == HttpURLConnection.HTTP_OK) {
                                    try (InputStream imgStream = imgConn.getInputStream()) {
                                        Bitmap imageBitmap = BitmapFactory.decodeStream(imgStream);
                                        if (imageBitmap != null) {
                                            bitmapList.add(imageBitmap);
                                            Log.d("PhotoViewer", "Image " + i + " loaded successfully");
                                        } else {
                                            Log.w("PhotoViewer", "Failed to decode image " + i);
                                        }
                                    }
                                } else {
                                    Log.w("PhotoViewer", "Image request failed with code: " + imgResponseCode);
                                }
                                imgConn.disconnect();
                            } catch (Exception e) {
                                Log.e("PhotoViewer", "Error loading image " + i + ": " + e.getMessage());
                                e.printStackTrace();
                            }
                        } else {
                            Log.d("PhotoViewer", "Post " + i + " has no image");
                        }
                    }
                } else {
                    Log.e("PhotoViewer", "API request failed with code: " + responseCode);
                    InputStream errorStream = conn.getErrorStream();
                    if (errorStream != null) {
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                        StringBuilder errorResult = new StringBuilder();
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null) {
                            errorResult.append(errorLine);
                        }
                        Log.e("PhotoViewer", "Error response: " + errorResult.toString());
                    }
                }
            } catch (IOException | JSONException e) {
                Log.e("PhotoViewer", "Error: " + e.getMessage(), e);
                e.printStackTrace();
            } finally {
                if (conn != null) conn.disconnect();
            }
            Log.d("PhotoViewer", "Total images loaded: " + bitmapList.size());
            return bitmapList;
        }

        @Override
        protected void onPostExecute(List<Bitmap> images) {
            if (isFinishing() || isDestroyed()) return;
            if (images == null || images.isEmpty()) {
                textView.setText("불러올 이미지가 없습니다.\nLogcat에서 오류를 확인하세요.");
                Toast.makeText(getApplicationContext(), "이미지를 불러올 수 없습니다. Logcat 확인", Toast.LENGTH_LONG).show();
            } else {
                textView.setText("이미지 " + images.size() + "개 로드 성공!");
                RecyclerView recyclerView = findViewById(R.id.recyclerView);
                ImageAdapter adapter = new ImageAdapter(images);
                recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                recyclerView.setAdapter(adapter);
                Toast.makeText(getApplicationContext(), "이미지 " + images.size() + "개 로드 완료", Toast.LENGTH_SHORT).show();
            }
        }
    }
}


