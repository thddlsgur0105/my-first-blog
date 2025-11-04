package com.example.photoviewer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView textView;
    String siteUrl = "http://127.0.0.1:8000";
    String token = "";  // 인증이 필요한 경우 토큰 입력
    CloadImage taskDownload;

    private static final int PICK_IMAGE_REQUEST = 1;
    private Uri selectedImageUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);

        // 🔹 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
            }
        }
    }

    // 🔹 서버에서 이미지 목록 가져오기
    public void onClickDownload(View v) {
        Log.d("PhotoViewer", "✅ onClickDownload clicked");
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(siteUrl + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();
    }

    // 🔹 갤러리에서 이미지 선택
    public void onClickUpload(View v) {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    // 🔹 선택된 이미지 URI 수신
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                Toast.makeText(this, "이미지 선택 완료", Toast.LENGTH_SHORT).show();
                new UploadImageTask().execute(selectedImageUri);
            }
        }
    }

    // 🔹 이미지 업로드 (multipart/form-data)
    private class UploadImageTask extends AsyncTask<Uri, Void, String> {
        @Override
        protected String doInBackground(Uri... uris) {
            String uploadUrl = siteUrl + "/api_root/Post/";
            Uri imageUri = uris[0];
            String boundary = "*****";
            String LINE_FEED = "\r\n";
            try {
                URL url = new URL(uploadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setUseCaches(false);
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Token " + token);
                }

                DataOutputStream request = new DataOutputStream(conn.getOutputStream());
                request.writeBytes("--" + boundary + LINE_FEED);
                request.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"upload.jpg\"" + LINE_FEED);
                request.writeBytes("Content-Type: image/jpeg" + LINE_FEED);
                request.writeBytes(LINE_FEED);

                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    request.write(buffer, 0, bytesRead);
                }
                inputStream.close();

                request.writeBytes(LINE_FEED);
                request.writeBytes("--" + boundary + "--" + LINE_FEED);
                request.flush();
                request.close();

                int responseCode = conn.getResponseCode();
                Log.d("PhotoViewer", "Upload response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                    return "업로드 성공!";
                } else {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                    StringBuilder errorMsg = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorMsg.append(line);
                    }
                    return "업로드 실패: " + errorMsg;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "오류: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
        }
    }

    // 🔹 서버에서 이미지 다운로드
    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmaps = new ArrayList<>();
            try {
                String apiUrl = urls[0];
                Log.d("PhotoViewer", "📡 Request to: " + apiUrl);
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.connect();

                int responseCode = conn.getResponseCode();
                Log.d("PhotoViewer", "✅ Response Code: " + responseCode);

                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONArray jsonArray = new JSONArray(sb.toString());
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject post = jsonArray.getJSONObject(i);
                        if (post.has("image")) {
                            String imageUrl = post.getString("image");

                            if (imageUrl == null || imageUrl.equals("null") || imageUrl.isEmpty()) {
                                continue;
                            }

                            // ✅ URL 보정 (중복 방지)
                            if (!imageUrl.startsWith("http")) {
                                imageUrl = siteUrl + imageUrl;
                            }

                            Log.d("PhotoViewer", "🖼 Image URL: " + imageUrl);

                            URL imgUrl = new URL(imageUrl);
                            HttpURLConnection imgConn = (HttpURLConnection) imgUrl.openConnection();
                            imgConn.connect();
                            InputStream is = imgConn.getInputStream();
                            Bitmap bitmap = BitmapFactory.decodeStream(is);
                            bitmaps.add(bitmap);
                            is.close();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("PhotoViewer", "❌ Error: " + e.getMessage(), e);
            }
            return bitmaps;
        }

        @Override
        protected void onPostExecute(List<Bitmap> bitmaps) {
            super.onPostExecute(bitmaps);

            if (bitmaps.isEmpty()) {
                Toast.makeText(getApplicationContext(), "표시할 이미지가 없습니다.", Toast.LENGTH_SHORT).show();
                textView.setText("서버에 이미지가 없습니다.");
            } else {
                Toast.makeText(getApplicationContext(), bitmaps.size() + "개의 이미지를 불러왔습니다.", Toast.LENGTH_SHORT).show();

                RecyclerView recyclerView = findViewById(R.id.recyclerView);
                LinearLayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
                layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
                recyclerView.setLayoutManager(layoutManager);
                
                // 부드러운 스크롤 및 성능 최적화
                recyclerView.setNestedScrollingEnabled(true);
                recyclerView.setHasFixedSize(false);
                recyclerView.setItemViewCacheSize(20);

                ImageAdapter adapter = new ImageAdapter(bitmaps);
                recyclerView.setAdapter(adapter);
                
                // 상단으로 스크롤
                recyclerView.smoothScrollToPosition(0);

                textView.setText("✨ 총 " + bitmaps.size() + "개의 이미지가 표시됩니다.");
            }
        }
    }
}
