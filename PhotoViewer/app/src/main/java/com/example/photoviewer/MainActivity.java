package com.example.photoviewer;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView textView;
    ProgressBar progressBar;
    SwipeRefreshLayout swipeRefreshLayout;
    RecyclerView recyclerView;
    ImageAdapter adapter;
    
    String siteUrl = "https://thddlsgur01050331.pythonanywhere.com";
    String token = "";
    CloadImage taskDownload;
    
    private List<Bitmap> currentBitmaps = new ArrayList<>();
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private Uri selectedImageUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        textView = findViewById(R.id.textView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerView = findViewById(R.id.recyclerView);
        
        // RecyclerView 초기 설정
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(false);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setNestedScrollingEnabled(false);  // SwipeRefreshLayout과 함께 사용할 때 필요
        
        // 🔹 Pull to Refresh 설정
        swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#00BCD4"),  // Blue
            Color.parseColor("#4CAF50"),  // Green
            Color.parseColor("#FF9800"),  // Orange
            Color.parseColor("#F44336")   // Red
        );
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadImages();
            }
        });

        // 🔹 권한 확인
        checkPermissions();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 🔹 서버에서 이미지 목록 가져오기
    public void onClickDownload(View v) {
        loadImages();
    }

    private void loadImages() {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(siteUrl + "/api_root/Post/");
    }

    // 🔹 갤러리에서 이미지 선택
    public void onClickUpload(View v) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

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

    // 🔹 이미지 업로드
    private class UploadImageTask extends AsyncTask<Uri, Void, String> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

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
            progressBar.setVisibility(View.GONE);
            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
            if (result.contains("성공")) {
                loadImages();
            }
        }
    }

    // 🔹 서버에서 이미지 다운로드
    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setRefreshing(true);
        }

        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmaps = new ArrayList<>();
            try {
                String apiUrl = urls[0];
                Log.d("PhotoViewer", "📡 Request to: " + apiUrl);
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Token " + token);
                }
                conn.setRequestProperty("Accept", "application/json");
                conn.connect();

                int responseCode = conn.getResponseCode();
                Log.d("PhotoViewer", "✅ API Response Code: " + responseCode);

                if (responseCode == 200) {
                    Log.d("PhotoViewer", "📥 Parsing JSON response...");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    String jsonString = sb.toString();
                    Log.d("PhotoViewer", "📄 JSON Response length: " + jsonString.length());
                    if (jsonString.length() > 0) {
                        Log.d("PhotoViewer", "📄 JSON Response (first 500 chars): " + jsonString.substring(0, Math.min(500, jsonString.length())));
                    }
                    
                    if (jsonString.trim().isEmpty()) {
                        Log.e("PhotoViewer", "❌ Empty JSON response");
                        return bitmaps;
                    }

                    JSONArray jsonArray = new JSONArray(jsonString);
                    Log.d("PhotoViewer", "📦 JSON Array size: " + jsonArray.length());
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject post = jsonArray.getJSONObject(i);
                        if (post.has("image")) {
                            String imageUrl = post.getString("image");

                            if (imageUrl == null || imageUrl.equals("null") || imageUrl.isEmpty()) {
                                Log.d("PhotoViewer", "⚠️ Empty image URL, skipping...");
                                continue;
                            }

                            if (!imageUrl.startsWith("http")) {
                                imageUrl = siteUrl + imageUrl;
                            }

                            Log.d("PhotoViewer", "🖼 Image URL: " + imageUrl);

                            try {
                                URL imgUrl = new URL(imageUrl);
                                HttpURLConnection imgConn = (HttpURLConnection) imgUrl.openConnection();
                                imgConn.setConnectTimeout(15000);
                                imgConn.setReadTimeout(15000);
                                imgConn.setRequestMethod("GET");
                                imgConn.connect();
                                
                                int imgResponseCode = imgConn.getResponseCode();
                                Log.d("PhotoViewer", "📸 Image Response Code: " + imgResponseCode);
                                
                                if (imgResponseCode == 200) {
                                    InputStream is = imgConn.getInputStream();
                                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                                    if (bitmap != null) {
                                        bitmaps.add(bitmap);
                                        Log.d("PhotoViewer", "✅ Image loaded successfully");
                                    } else {
                                        Log.e("PhotoViewer", "❌ Bitmap is null");
                                    }
                                    is.close();
                                } else {
                                    Log.e("PhotoViewer", "❌ Image load failed: " + imgResponseCode);
                                }
                                imgConn.disconnect();
                            } catch (Exception e) {
                                Log.e("PhotoViewer", "❌ Error loading image: " + e.getMessage(), e);
                            }
                        }
                    }
                    Log.d("PhotoViewer", "📊 Total images loaded: " + bitmaps.size());
                } else {
                    Log.e("PhotoViewer", "❌ API Error: Response code " + responseCode);
                    try {
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                        StringBuilder errorMsg = new StringBuilder();
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            errorMsg.append(line);
                        }
                        Log.e("PhotoViewer", "❌ Error message: " + errorMsg.toString());
                    } catch (Exception e) {
                        Log.e("PhotoViewer", "❌ Failed to read error stream: " + e.getMessage());
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e("PhotoViewer", "❌ Fatal Error: " + e.getMessage(), e);
                e.printStackTrace();
            }
            return bitmaps;
        }

        @Override
        protected void onPostExecute(List<Bitmap> bitmaps) {
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);

            currentBitmaps = bitmaps;

            if (bitmaps.isEmpty()) {
                Toast.makeText(getApplicationContext(), "표시할 이미지가 없습니다.", Toast.LENGTH_SHORT).show();
                textView.setText("서버에 이미지가 없습니다.");
                adapter = new ImageAdapter(new ArrayList<>());
                recyclerView.setAdapter(adapter);
            } else {
                Toast.makeText(getApplicationContext(), bitmaps.size() + "개의 이미지를 불러왔습니다.", Toast.LENGTH_SHORT).show();

                adapter = new ImageAdapter(bitmaps);
                
                // 🔹 이미지 클릭/롱클릭 이벤트 설정
                adapter.setOnImageClickListener(new ImageAdapter.OnImageClickListener() {
                    @Override
                    public void onImageClick(Bitmap bitmap, int position) {
                        // 🔹 이미지 상세보기 (확대/줌)
                        // Bitmap을 파일로 저장하고 URI 전달 (크기 제한 회피)
                        try {
                            File cacheDir = getCacheDir();
                            // 이전 파일 삭제 (캐시 정리)
                            File[] oldFiles = cacheDir.listFiles((dir, name) -> name.startsWith("detail_image_"));
                            if (oldFiles != null) {
                                for (File oldFile : oldFiles) {
                                    oldFile.delete();
                                }
                            }
                            
                            File imageFile = new File(cacheDir, "detail_image_" + position + "_" + System.currentTimeMillis() + ".jpg");
                            FileOutputStream fos = new FileOutputStream(imageFile);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                            fos.flush();
                            fos.close();
                            
                            Log.d("PhotoViewer", "💾 Image saved to: " + imageFile.getAbsolutePath());
                            
                            Uri imageUri = FileProvider.getUriForFile(MainActivity.this,
                                    "com.example.photoviewer.fileprovider", imageFile);
                            
                            Log.d("PhotoViewer", "🔗 FileProvider URI: " + imageUri.toString());
                            
                            Intent intent = new Intent(MainActivity.this, ImageDetailActivity.class);
                            intent.putExtra("image_uri", imageUri.toString());
                            intent.putExtra("position", position);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);
                        } catch (Exception e) {
                            Log.e("PhotoViewer", "❌ Error saving bitmap for detail view: " + e.getMessage(), e);
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, "이미지 열기 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onImageLongClick(Bitmap bitmap, int position, View view) {
                        // 🔹 공유/저장 메뉴 표시
                        showImageMenu(bitmap, view);
                    }
                });
                
                recyclerView.setAdapter(adapter);
                adapter.notifyDataSetChanged();
                recyclerView.post(() -> {
                    recyclerView.smoothScrollToPosition(0);
                });
                textView.setText("✨ 총 " + bitmaps.size() + "개의 이미지가 표시됩니다.");
                Log.d("PhotoViewer", "✅ Adapter set with " + bitmaps.size() + " images");
            }
        }
    }

    // 🔹 이미지 공유/저장 메뉴
    private void showImageMenu(Bitmap bitmap, View view) {
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.getMenu().add("공유하기");
        popupMenu.getMenu().add("저장하기");
        
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("공유하기")) {
                shareImage(bitmap);
            } else if (item.getTitle().equals("저장하기")) {
                saveImage(bitmap);
            }
            return true;
        });
        
        popupMenu.show();
    }

    // 🔹 이미지 공유 기능
    private void shareImage(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();
            File file = new File(cachePath, "share_image_" + System.currentTimeMillis() + ".jpg");
            
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();

            Uri imageUri = FileProvider.getUriForFile(this,
                    "com.example.photoviewer.fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/jpeg");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "이미지 공유하기"));
        } catch (Exception e) {
            Toast.makeText(this, "공유 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("PhotoViewer", "Share error: " + e.getMessage());
        }
    }

    // 🔹 이미지 저장 기능
    private void saveImage(Bitmap bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 이상
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "PhotoViewer_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoViewer");

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try {
                    java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    out.close();
                    Toast.makeText(this, "이미지가 갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "저장 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("PhotoViewer", "Save error: " + e.getMessage());
                }
            }
        } else {
            // Android 9 이하
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                return;
            }

            try {
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File photoViewerDir = new File(picturesDir, "PhotoViewer");
                if (!photoViewerDir.exists()) {
                    photoViewerDir.mkdirs();
                }

                File file = new File(photoViewerDir, "PhotoViewer_" + System.currentTimeMillis() + ".jpg");
                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.flush();
                fos.close();

                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(file));
                sendBroadcast(mediaScanIntent);

                Toast.makeText(this, "이미지가 갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "저장 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("PhotoViewer", "Save error: " + e.getMessage());
            }
        }
    }
}

