package com.example.viewit.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;

// categories/{categoryId} 문서. 문서 ID는 YouTube 카테고리 ID 문자열이다.
public class Category {

    @DocumentId
    private String categoryId;          // 문서 ID(YouTube 카테고리 ID). 필드로는 저장하지 않음
    private String name;                // 카테고리 이름
    private long sortOrder;             // 표시 순서
    private boolean isActive;           // 앱에서 사용하는지

    public Category() {
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(long sortOrder) {
        this.sortOrder = sortOrder;
    }

    // boolean의 isXxx()는 Firestore가 "active"로 읽으므로 필드 이름을 isActive로 고정한다
    @PropertyName("isActive")
    public boolean isActive() {
        return isActive;
    }

    @PropertyName("isActive")
    public void setActive(boolean active) {
        isActive = active;
    }
}
