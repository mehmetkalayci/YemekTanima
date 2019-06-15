package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    Button btnSend, btnLoad;
    ImageView imgViewSelectedImage;

    String selectedImagePath = null;

    private static final int TAKE_PHOTO_REQUEST_CODE = 0;
    private static final int CHOOSE_FROM_GALLERY_REQUEST_CODE = 1;

    public static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1000;

    private static final String TAG = "YEMEK TANIMA APP:";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSend = findViewById(R.id.btnSend);
        btnLoad = findViewById(R.id.btnLoad);

        btnSend.setOnClickListener(MainActivity.this);
        btnLoad.setOnClickListener(MainActivity.this);

        imgViewSelectedImage = findViewById(R.id.imgSelectedImage);

        if (!checkAndRequestPermissions()) {
            backgroundThreadAlertDialog(MainActivity.this, "Uygulamaya gerekli izinler verilmedi!\nUygulama kapatılacak.", ":(");
            return;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            switch (requestCode) {
                case CHOOSE_FROM_GALLERY_REQUEST_CODE:
                    selectedImagePath = getPathFromURI(data.getData());
                    imgViewSelectedImage.setImageBitmap(BitmapFactory.decodeFile(selectedImagePath));

                    Log.d(TAG, "CHOOSE_FROM_GALLERY_REQUEST_CODE onActivityResult: " + selectedImagePath);
                    break;
                case TAKE_PHOTO_REQUEST_CODE:
                    Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");

                    File destination = new File(Environment.getExternalStorageDirectory(), "yemek-tanima-app.jpg");
                    try {
                        FileOutputStream out = new FileOutputStream(destination);
                        imageBitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
                        out.flush();
                        out.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        backgroundThreadAlertDialog(MainActivity.this, "Fotograf kaydedilirken hata oluştu!\n" + e.getMessage(), "DOSYA HATASI");
                    }

                    if (destination.exists()) {
                        selectedImagePath = destination.getAbsolutePath();
                        imgViewSelectedImage.setImageBitmap(BitmapFactory.decodeFile(selectedImagePath));
                    } else {
                        backgroundThreadAlertDialog(MainActivity.this, "Fotograf bulunamadı!", "HATA");
                    }

                    Log.d(TAG, "TAKE_PHOTO_REQUEST_CODE onActivityResult: " + selectedImagePath);
                    break;
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnLoad:
                selectImage(MainActivity.this);
                break;
            case R.id.btnSend:

                if (selectedImagePath != null && selectedImagePath != "") {
                    File selectedImageFile = new File(selectedImagePath);
                    if (selectedImageFile.exists()) {
                        try {
                            UploadImage(selectedImageFile);
                        } catch (IOException e) {
                            backgroundThreadAlertDialog(MainActivity.this, e.getMessage(), "UPLOAD HATASI");
                        }
                    } else {
                        backgroundThreadAlertDialog(MainActivity.this, "Resim Bulunamadı!", "DOSYA HATASI");
                    }
                } else {
                    backgroundThreadAlertDialog(MainActivity.this, "Önce resim seçin.", "Opps!");
                }
                break;
        }
    }

    private String UploadImage(File file) throws IOException {

        final String result = "";

        OkHttpClient client = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.COMPATIBLE_TLS))
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "deneme.jpg", RequestBody.create(file, MediaType.parse("image/*")))
                .build();

        final Request request = new Request.Builder()
                .url("https://yemek-tanima.herokuapp.com/predict")
                .post(requestBody)
                .build();

        Call call = client.newCall(request);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.d(TAG, "onFailure: " + e.getMessage());
                backgroundThreadAlertDialog(MainActivity.this, e.getMessage(), "UPLOAD HATASI");
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                // response.body().string(); bu kod sadece 1 kez çalıştırılmalı
                // Çünkü response 1 tane ve 1 kez consume edilebilir.
                String responseMessage = response.body().string();
                backgroundThreadAlertDialog(MainActivity.this, responseMessage, "TAHMİN EDİLEN");
            }
        });
        return result;
    }

    public static void backgroundThreadAlertDialog(final Context context, final String message, final String title) {
        if (context != null && message != null && title != null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {

                @Override
                public void run() {
                    AlertDialog alertDialog = new AlertDialog.Builder(context)
                            .setTitle(title)
                            .setMessage(message)
                            .show();
                }
            });
        }
    }

    private void selectImage(Context context) {
        final CharSequence[] options = {"Kamera", "Galeriden Seç", "İptal"};

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Resim seçin");

        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {

                if (options[item].equals("Kamera")) {
                    Intent takePicture = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(takePicture, TAKE_PHOTO_REQUEST_CODE);
                } else if (options[item].equals("Galeriden Seç")) {
                    Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    pickPhoto.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png"});
                    startActivityForResult(pickPhoto, CHOOSE_FROM_GALLERY_REQUEST_CODE);
                } else if (options[item].equals("İptal")) {
                    dialog.dismiss();
                }


            }
        });
        builder.show();
    }

    private boolean checkAndRequestPermissions() {
        int permissionINTERNET = ContextCompat.checkSelfPermission(this, android.Manifest.permission.INTERNET);
        int permissionCAMERA = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA);
        int permissionWRITE_EXTERNAL_STORAGE = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionREAD_EXTERNAL_STORAGE = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        List<String> listPermissionsNeeded = new ArrayList<>();

        if (permissionINTERNET != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.INTERNET);
        }
        if (permissionCAMERA != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.CAMERA);
        }
        if (permissionWRITE_EXTERNAL_STORAGE != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (permissionREAD_EXTERNAL_STORAGE != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), REQUEST_ID_MULTIPLE_PERMISSIONS);
            return false;
        }
        return true;
    }

    private String getPathFromURI(Uri uri) {
        String path = null;
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, filePathColumn, null, null, null);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        path = cursor.getString(columnIndex);
        cursor.close();
        return path;
    }
}