package com.example.viewit;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CloudFirestoreActivity extends AppCompatActivity {

    private static final String TAG = "CJW"; //태그

    private FirebaseFirestore db; //db 객체 생성

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cloud_firestore);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeCloudFirestore(); //db 변수에 객체 할당하는 함수

        addData();
        //getADocument();
        //addDataFromCustomObject();
        //getDocumentAsCustomObject();
        //getMultipleDocumentsFromACollection();
        //getMultipleDocumentsFromACollection();
        //getDocumentsWidthOrderAndLimitData();
        //createSubCollectionWithTheSameID();
        //collectionsGroupQuery();
    }

    private void initializeCloudFirestore() {
        db = FirebaseFirestore.getInstance();
    }

    private void addData() {
        CollectionReference ratings = db.collection("ratings");

        Map<String, Object> rating = new HashMap<>();
        rating.put("videoId", "video001");
        rating.put("score", 4.5);
        rating.put("comment", "설명이 이해하기 쉬워요");

        // 고정 ID로 저장하므로 다시 실행하면 같은 문서를 덮어씁니다.
        ratings.document("testRating")
                .set(rating)
                .addOnSuccessListener(unused -> Log.d(TAG, "평가 저장 성공"))
                .addOnFailureListener(e -> Log.e(TAG, "평가 저장 실패", e));
    }
}
