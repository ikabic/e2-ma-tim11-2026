package com.slagalica.app.repository;

public interface RepositoryCallback<T> {
    void onSuccess(T result);
    void onFailure(Exception e);
}
