package com.example.viewit.data;

import androidx.annotation.NonNull;

import com.example.viewit.data.model.FirestorePaths;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 온보딩 저장 (3.3). 관심 카테고리를 users/{uid}.categoryIds에 저장한다.
public class OnboardingRepository {

    public interface Callback {
        void onSuccess();

        void onError(@NonNull Exception error);
    }

    public void saveCategoryIds(@NonNull String uid, @NonNull List<String> categoryIds, @NonNull Callback callback) {
        Map<String, Object> update = new HashMap<>();
        update.put("categoryIds", categoryIds);
        update.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance()
            .collection(FirestorePaths.USERS)
            .document(uid)
            .update(update)
            .addOnSuccessListener(unused -> callback.onSuccess())
            .addOnFailureListener(callback::onError);
    }
}
