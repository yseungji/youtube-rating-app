package com.example.viewit;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.viewit.databinding.ActivityCloudFirestoreBinding;
import androidx.annotation.NonNull;

import com.example.viewit.data.RatingRepository;
import com.example.viewit.data.FeedPagingSamples;
import com.example.viewit.data.FollowingFeedSamples;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Source;
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

    private static final String TAG = "FeedPagingSample";

    private FirebaseFirestore db; //db 객체 생성
    private ActivityCloudFirestoreBinding binding;
    private final FollowingFeedSamples followingSamples = new FollowingFeedSamples();
    private boolean sampleBusy;
    private boolean deletingSamples;
    private int sampleCompleted;
    private int sampleChanged;
    private int sampleSkipped;
    private int sampleFailed;

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

        // 화면을 여는 것만으로 서버에 쓰지 않는다. 눌렀을 때만 실행한다.
        setSampleButtonsEnabled(true);
        binding.pagingCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                managePagingSamples(false);
            }
        });
        binding.pagingDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                managePagingSamples(true);
            }
        });
        binding.followingCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manageFollowingSamples(false);
            }
        });
        binding.followingDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manageFollowingSamples(true);
            }
        });
        binding.singleSample.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!sampleBusy) {
                    addData();
                }
            }
        });
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

    // 기존 실제 영상 샘플 1개 기능도 버튼으로 실행할 수 있다.
    private void addData() {
        if (!BuildConfig.DEBUG) {
            showSingleSampleStatus(R.string.feed_sample_debug_only);
            return;
        }
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showSingleSampleStatus(R.string.feed_sample_login_required);
            return;
        }
        final String uid = user.getUid();
        final String videoId = "jNQXAC9IVRw"; // 기존 영상 정보 확인 화면에서도 쓰는 실제 영상
        final RatingRepository repository = new RatingRepository();
        showSingleSampleStatus(R.string.feed_sample_saving);

        // 본인이 이미 평가한 영상이면 기존 평가를 유지한다.
        repository.getRating(uid, videoId)
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snapshot) {
                        if (snapshot.exists()) {
                            showSingleSampleStatus(R.string.feed_sample_exists);
                            return;
                        }
                        // 실제 categoryId를 사용하고 제목·썸네일 캐시도 준비한다.
                        new VideoRepository().getVideo(videoId, new VideoRepository.Callback<Video>() {
                            @Override
                            public void onSuccess(Video video) {
                                FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                                if (current == null || !uid.equals(current.getUid())) {
                                    showSingleSampleStatus(R.string.feed_sample_login_required);
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
                                                showSingleSampleStatus(R.string.feed_sample_saved);
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


    // 반복문으로 45개를 준비한다. 각 get/set/delete 결과를 세어 일부 실패도 표시한다.
    private void managePagingSamples(final boolean deleting) {
        if (sampleBusy) {
            return;
        }
        if (!BuildConfig.DEBUG) {
            showSingleSampleStatus(R.string.feed_sample_debug_only);
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showSingleSampleStatus(R.string.feed_sample_login_required);
            return;
        }
        final String uid = user.getUid();
        sampleBusy = true;
        deletingSamples = deleting;
        sampleCompleted = 0;
        sampleChanged = 0;
        sampleSkipped = 0;
        sampleFailed = 0;
        setSampleButtonsEnabled(false);
        showPagingProgress();
        Log.d(TAG, (deleting ? "샘플 삭제 시작" : "샘플 생성 시작")
                + ": count=" + FeedPagingSamples.COUNT);

        for (int number = 1; number <= FeedPagingSamples.COUNT; number++) {
            final int sampleNumber = number;
            final Rating sample = FeedPagingSamples.create(uid, sampleNumber);
            final DocumentReference reference = db.collection("ratings").document(
                    Rating.documentId(uid, sample.getVideoId()));
            // 서버 확인에 실패했을 때는 '없는 문서'로 간주해 덮어쓰지 않는다.
            reference.get(Source.SERVER)
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot snapshot) {
                            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                            if (current == null || !uid.equals(current.getUid())) {
                                pagingItemError(sampleNumber, new IllegalStateException(
                                        "로그인 계정이 변경되어 작업을 중단했습니다."));
                                return;
                            }
                            if ((!deleting && snapshot.exists())
                                    || (deleting && !snapshot.exists())) {
                                finishPagingItem(false, false);
                                return;
                            }
                            if (deleting) {
                                try {
                                    Rating stored = snapshot.toObject(Rating.class);
                                    if (stored == null || !FeedPagingSamples.isSample(stored)
                                            || !uid.equals(stored.getUserId())
                                            || !sample.getVideoId().equals(stored.getVideoId())
                                            || stored.getLikeCount() != 0
                                            || stored.getCommentCount() != 0) {
                                        Log.w(TAG, "샘플 표시·작성자 불일치 또는 반응 있음: 삭제 건너뜀 #"
                                                + sampleNumber);
                                        finishPagingItem(false, false);
                                        return;
                                    }
                                } catch (RuntimeException error) {
                                    pagingItemError(sampleNumber, error);
                                    return;
                                }
                            }
                            Task<Void> operation = deleting
                                    ? reference.delete() : reference.set(sample);
                            operation.addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void unused) {
                                    finishPagingItem(true, false);
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception error) {
                                    pagingItemError(sampleNumber, error);
                                }
                            });
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception error) {
                            pagingItemError(sampleNumber, error);
                        }
                    });
        }
    }

    private void pagingItemError(int number, Exception error) {
        Log.e(TAG, (deletingSamples ? "샘플 삭제" : "샘플 생성")
                + " 실패 #" + number, error);
        finishPagingItem(false, true);
    }

    private void finishPagingItem(boolean changed, boolean failed) {
        sampleCompleted++;
        if (failed) {
            sampleFailed++;
        } else if (changed) {
            sampleChanged++;
        } else {
            sampleSkipped++;
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (sampleCompleted == FeedPagingSamples.COUNT) {
            sampleBusy = false;
            setSampleButtonsEnabled(true);
            binding.firestoreStatus.setText(getString(R.string.feed_paging_done,
                    getString(deletingSamples ? R.string.feed_paging_deleting
                            : R.string.feed_paging_creating),
                    sampleChanged, sampleSkipped, sampleFailed));
            Log.d(TAG, "샘플 작업 종료: success=" + sampleChanged
                    + ", skipped=" + sampleSkipped + ", failed=" + sampleFailed);
        } else {
            showPagingProgress();
        }
    }

    private void showPagingProgress() {
        binding.firestoreStatus.setText(getString(R.string.feed_paging_working,
                getString(deletingSamples ? R.string.feed_paging_deleting
                        : R.string.feed_paging_creating),
                sampleCompleted, FeedPagingSamples.COUNT));
    }

    private void manageFollowingSamples(final boolean deleting) {
        if (sampleBusy) {
            return;
        }
        if (!BuildConfig.DEBUG) {
            showSingleSampleStatus(R.string.feed_sample_debug_only);
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showSingleSampleStatus(R.string.feed_sample_login_required);
            return;
        }
        final String operation = getString(deleting ? R.string.feed_follow_sample_deleting
                : R.string.feed_follow_sample_creating);
        sampleBusy = true;
        setSampleButtonsEnabled(false);
        followingSamples.run(db, user.getUid(), deleting, new FollowingFeedSamples.Callback() {
            @Override
            public void onProgress(int completed, int total) {
                if (!isFinishing() && !isDestroyed()) {
                    binding.firestoreStatus.setText(getString(R.string.feed_paging_working,
                            operation, completed, total));
                }
            }

            @Override
            public void onComplete(int changed, int skipped, int failed) {
                if (!isFinishing() && !isDestroyed()) {
                    sampleBusy = false;
                    setSampleButtonsEnabled(true);
                    binding.firestoreStatus.setText(getString(R.string.feed_follow_sample_done,
                            operation, changed, skipped, failed));
                }
            }
        });
    }

    private void setSampleButtonsEnabled(boolean enabled) {
        binding.pagingCreate.setEnabled(enabled && BuildConfig.DEBUG);
        binding.pagingDelete.setEnabled(enabled && BuildConfig.DEBUG);
        binding.singleSample.setEnabled(enabled && BuildConfig.DEBUG);
        binding.followingCreate.setEnabled(enabled && BuildConfig.DEBUG);
        binding.followingDelete.setEnabled(enabled && BuildConfig.DEBUG);
    }

    private void showSingleSampleStatus(int message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        sampleBusy = message == R.string.feed_sample_saving;
        setSampleButtonsEnabled(!sampleBusy);
        binding.firestoreStatus.setText(message);
    }

    private void showSampleError(Exception error) {
        showSingleSampleStatus(R.string.feed_sample_error);
        Log.e(TAG, "테스트 평가 준비 실패", error);
    }
}
