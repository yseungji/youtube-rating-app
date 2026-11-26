package com.example.viewit.ui.profile;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.viewit.R;
import com.example.viewit.data.BlockRepository;
import com.example.viewit.data.model.BlockedUser;
import com.example.viewit.databinding.ActivityBlockedUsersBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

// 설정 → 차단한 사용자 (6.3). 최근 차단순 목록, [차단 해제] → 확인창 → 목록에서 빠짐.
public class BlockedUsersActivity extends AppCompatActivity {

    private static final String TAG = "BlockedUsers";

    private ActivityBlockedUsersBinding binding;
    private final BlockRepository blockRepository = new BlockRepository();
    private BlockedUserAdapter adapter;
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityBlockedUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        myUid = user.getUid();
        applySystemBarPadding();
        setupEmptyView();

        // ← 닫기, 목록 연결 ([차단 해제] → 확인창)
        binding.blockedBack.getRoot().setOnClickListener(v -> finish());
        adapter = new BlockedUserAdapter(new BlockedUserAdapter.OnUnblockClickListener() {
            @Override
            public void onUnblockClick(BlockedUser item) {
                confirmUnblock(item);
            }
        });
        binding.blockedList.setLayoutManager(new LinearLayoutManager(this));
        binding.blockedList.setAdapter(adapter);

        loadBlockedUsers();
    }

    // 차단 해제 확인창. "해제"를 누르면 unblock(item)
    private void confirmUnblock(BlockedUser item) {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.unblock_dialog_title, item.getDisplayName()))
            .setMessage(R.string.unblock_dialog_message)
            .setPositiveButton(R.string.unblock_dialog_confirm, (dialog,which)->unblock(item))
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void unblock(BlockedUser item) {
        blockRepository.unblock(myUid, item.getUid(), new BlockRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(BlockedUsersActivity.this, R.string.unblock_done, Toast.LENGTH_SHORT).show();
                loadBlockedUsers();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(BlockedUsersActivity.this, R.string.unblock_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadBlockedUsers() {
        binding.blockedLoading.setVisibility(View.VISIBLE);
        binding.blockedEmpty.getRoot().setVisibility(View.GONE);
        blockRepository.loadBlockedUsers(myUid, new BlockRepository.Callback<List<BlockedUser>>() {
            @Override
            public void onSuccess(List<BlockedUser> items) {
                showItems(items);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.w(TAG, "load failed", error);
                showItems(new ArrayList<>());
                Toast.makeText(BlockedUsersActivity.this, R.string.blocked_users_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showItems(List<BlockedUser> items) {
        if (isDestroyed()) {
            return;
        }
        binding.blockedLoading.setVisibility(View.GONE);
        if (adapter != null) {
            adapter.setItems(items);
        }
        // 비었으면 빈 화면, 있으면 목록
        if(items.isEmpty()) {
            binding.blockedEmpty.getRoot().setVisibility(View.VISIBLE);
            binding.blockedList.setVisibility(View.GONE);
        }
        else {
            binding.blockedEmpty.getRoot().setVisibility(View.GONE);
            binding.blockedList.setVisibility(View.VISIBLE);
        }
    }

    private void setupEmptyView() {
        binding.blockedEmpty.emptyIcon.setImageResource(R.drawable.ic_block);
        binding.blockedEmpty.emptyTitle.setText(R.string.blocked_users_empty_title);
        binding.blockedEmpty.emptyMessage.setText(R.string.blocked_users_empty_message);
    }

    private void applySystemBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
}
