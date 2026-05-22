package com.slagalica.app.viewmodel;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.slagalica.app.model.RankingCycle;
import com.slagalica.app.model.RankingEntry;
import com.slagalica.app.repository.RankingRepository;

import java.util.List;

public class RankingViewModel extends ViewModel {

    public enum CycleType { WEEKLY, MONTHLY }

    private final RankingRepository repo = new RankingRepository();

    private final MutableLiveData<List<RankingEntry>> entries  = new MutableLiveData<>();
    private final MutableLiveData<RankingCycle> cycle  = new MutableLiveData<>();
    private final MutableLiveData<Boolean>  loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<CycleType> activeType  = new MutableLiveData<>(CycleType.WEEKLY);

    private final Handler  handler  = new Handler(Looper.getMainLooper());
    private static final long REFRESH_MS = 2 * 60 * 1000L;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    public RankingViewModel() {
        startAutoRefresh();
    }

    public LiveData<List<RankingEntry>> getEntries()    { return entries; }
    public LiveData<RankingCycle> getCycle() { return cycle; }
    public LiveData<Boolean>  getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<CycleType> getActiveType() { return activeType; }

    public void selectType(CycleType type) {
        activeType.setValue(type);
        refresh();
    }

    public void refresh() {
        CycleType type   = activeType.getValue() != null ? activeType.getValue() : CycleType.WEEKLY;
        String    typeStr = type == CycleType.WEEKLY ? "weekly" : "monthly";
        loading.setValue(true);

        repo.fetchCurrentCycle(typeStr, new com.slagalica.app.repository.RepositoryCallback<RankingCycle>() {
            @Override public void onSuccess(RankingCycle result) {
                cycle.setValue(result);
            }
            @Override public void onFailure(Exception e) { /* non-fatal */ }
        });

        repo.fetchLeaderboard(typeStr, new com.slagalica.app.repository.RepositoryCallback<List<RankingEntry>>() {
            @Override public void onSuccess(List<RankingEntry> result) {
                entries.setValue(result);
                loading.setValue(false);
            }
            @Override public void onFailure(Exception e) {
                error.setValue(e.getMessage());
                loading.setValue(false);
            }
        });
    }

    private void startAutoRefresh() {
        handler.post(refreshTask);
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacks(refreshTask);
        super.onCleared();
    }
}