package com.example.viewit.ui.comment;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.viewit.R;
import com.example.viewit.data.model.Comment;
import com.example.viewit.databinding.ItemCommentBinding;
import com.example.viewit.ui.common.TimeAgoFormat;

import java.util.Date;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    public interface OnCommentActionListener {
        void onReplyClick(Comment comment);
        void onLikeClick(Comment comment);
    }

    private final List<Comment> comments;
    private final OnCommentActionListener listener;

    public CommentAdapter(List<Comment> comments, OnCommentActionListener listener) {
        this.comments = comments;
        this.listener = listener;
    }

    public void setComments(List<Comment> newComments) {
        comments.clear();
        comments.addAll(newComments);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCommentBinding binding = ItemCommentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CommentViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        holder.bind(comments.get(position));
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    //뷰홀더
    public class CommentViewHolder extends RecyclerView.ViewHolder {
        private final ItemCommentBinding binding;

        public CommentViewHolder(@NonNull ItemCommentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Comment comment) {
            Context context = binding.getRoot().getContext();

            // 1단계 답글인 경우 왼쪽에 여백(들여쓰기) 적용
            int leftPadding = comment.isReply() ? (int) (32 * context.getResources().getDisplayMetrics().density) : 0;
            binding.commentRoot.setPadding(leftPadding, binding.commentRoot.getPaddingTop(),
                    binding.commentRoot.getPaddingRight(), binding.commentRoot.getPaddingBottom());

            // 작성자 및 시각 표시
            String name = comment.getAuthorName() != null ? comment.getAuthorName() : "익명";
            binding.commentAuthor.authorName.setText(name);
            binding.commentAuthor.authorAvatar.setText(name.substring(0, name.offsetByCodePoints(0, 1)));

            String timeText = comment.getCreatedAt() == null ? ""
                    : TimeAgoFormat.format(context, comment.getCreatedAt().toDate(), new Date());
            String username = comment.getAuthorUsername();
            binding.commentAuthor.authorMeta.setText(username == null || username.isEmpty()
                    ? timeText : "@" + username + " · " + timeText);

            // 본문 및 언급(@아이디) 강조
            String content = comment.getContent() != null ? comment.getContent() : "";
            if (comment.getTargetUsername() != null && !comment.getTargetUsername().isEmpty()) {
                String mentionPrefix = "@" + comment.getTargetUsername() + " ";
                SpannableString spannable = new SpannableString(mentionPrefix + content);
                int coralColor = ContextCompat.getColor(context, R.color.positive);
                spannable.setSpan(new ForegroundColorSpan(coralColor), 0, mentionPrefix.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                binding.commentContent.setText(spannable);
            } else {
                binding.commentContent.setText(content);
            }

            // 좋아요 수 및 빨간 하트 상태 바인딩
            updateLikeStatus(comment);

            // 답글 달기 클릭
            binding.commentActionReply.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onReplyClick(comment);
                    }
                }
            });

            // 좋아요 클릭
            binding.commentActionLike.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onLikeClick(comment);
                    }
                }
            });
        }

        private void updateLikeStatus(Comment comment) {
            Context context = binding.getRoot().getContext();
            boolean liked = comment.isLikedByCurrentUser();

            binding.commentLikeCount.setText(String.valueOf(Math.max(0, comment.getLikeCount())));

            int iconRes = liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline;
            int colorRes = liked ? R.color.positive : R.color.text_secondary;

            binding.commentLikeIcon.setImageResource(iconRes);
            int color = ContextCompat.getColor(context, colorRes);
            binding.commentLikeIcon.setImageTintList(ColorStateList.valueOf(color));
            binding.commentLikeCount.setTextColor(color);
        }
    }
}
