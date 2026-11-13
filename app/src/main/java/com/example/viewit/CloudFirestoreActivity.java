package com.example.viewit;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.viewit.databinding.ActivityCloudFirestoreBinding;
import androidx.annotation.NonNull;

import com.example.viewit.data.RatingRepository;
import com.example.viewit.data.VideoRepository;
import com.example.viewit.data.model.Rating;
import com.example.viewit.data.model.Video;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class CloudFirestoreActivity extends AppCompatActivity {

    private static final String TAG = "CJW"; //태그

    private FirebaseFirestore db; //db 객체 생성
    private ActivityCloudFirestoreBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityCloudFirestoreBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
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

    // 기존 onCreate()의 addData() 호출을 그대로 사용한다.
    // 디버그 빌드에서 이 화면을 열면 본인 계정에 공개 평가 1개를 저장한다.
    private void addData() {
        if (!BuildConfig.DEBUG) {
            binding.firestoreStatus.setText(R.string.feed_sample_debug_only);
            return;
        }
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            binding.firestoreStatus.setText(R.string.feed_sample_login_required);
            return;
        }
        final String uid = user.getUid();
        final String videoId = "jNQXAC9IVRw"; // 기존 영상 정보 확인 화면에서도 쓰는 실제 영상
        final RatingRepository repository = new RatingRepository();
        binding.firestoreStatus.setText(R.string.feed_sample_saving);

        // 본인이 이미 평가한 영상이면 기존 평가를 유지한다.
        repository.getRating(uid, videoId)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (snapshot.exists()) {
                            binding.firestoreStatus.setText(R.string.feed_sample_exists);
                            return;
                        }
                        // 실제 categoryId를 사용하고 제목·썸네일 캐시도 준비한다.
                        new VideoRepository().getVideo(videoId, new VideoRepository.Callback<Video>() {
                            @Override
                            public void onSuccess(Video video) {
                                FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                                if (current == null || !uid.equals(current.getUid())) {
                                    binding.firestoreStatus.setText(R.string.feed_sample_login_required);
                                    return;
                                }
                                Rating rating = new Rating();
                                rating.setVideoId(videoId);
                                rating.setCategoryId(video.getCategoryId());
                                rating.setScore(4);
                                rating.setComment("피드 표시 확인용 테스트 평가입니다.");
                                rating.setVisibility(Rating.VISIBILITY_PUBLIC);
                                // 기존 저장 함수가 UID·반응 수·서버 작성 시각을 채운다.
                                repository.saveRating(rating)
                                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                                            @Override
                                            public void onSuccess(Void unused) {
                                                binding.firestoreStatus.setText(R.string.feed_sample_saved);
                                                Log.d(TAG, "테스트 공개 평가 저장 성공");
                                            }
                                        })
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception error) {
                                                showSampleError(error);
                                            }
                                        });
                            }

                            @Override
                            public void onError(@NonNull Exception error) {
                                showSampleError(error);
                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception error) {
                        showSampleError(error);
                    }
                });
    }

    private void showSampleError(Exception error) {
        binding.firestoreStatus.setText(R.string.feed_sample_error);
        Log.e(TAG, "테스트 평가 준비 실패", error);
    }
}
