package com.slagalica.app.viewmodel;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.slagalica.app.model.Region;
import com.slagalica.app.repository.RegionRepository;
import com.slagalica.app.repository.RepositoryCallback;

import org.osmdroid.util.GeoPoint;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class RegionViewModel extends ViewModel {

    private static final long REFRESH_MS = 2 * 60 * 1000L;

    private final RegionRepository regionRepo = new RegionRepository();

    private final MutableLiveData<Map<String, List<GeoPoint>>> regionBorderData = new MutableLiveData<>();
    private final MutableLiveData<List<Region>> leaderboard = new MutableLiveData<>();
    private final MutableLiveData<Map<String, List<GeoPoint>>> playerDots = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Integer>> activePlayersCount = new MutableLiveData<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            fetchLeaderboard();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private boolean isBootstrapped = false;

    public RegionViewModel() {}

    public void bootstrapRegions(Context context) {
        if (isBootstrapped) return;
        isBootstrapped = true;

        regionRepo.loadRegions(context.getApplicationContext(), new RepositoryCallback<>() {
            @Override
            public void onSuccess(List<Region> result) {
                startAutoRefresh();
                fetchPlayerDots();
                startObservingActivePlayers();
            }
            @Override
            public void onFailure(Exception e) { startAutoRefresh(); }
        });
    }

    public LiveData<Map<String, List<GeoPoint>>> getRegionBorderData() { return regionBorderData; }
    public LiveData<List<Region>> getLeaderboard() { return leaderboard; }
    public LiveData<Map<String, List<GeoPoint>>> getPlayerDots() { return playerDots; }
    public LiveData<Map<String, Integer>> getActivePlayersCount() { return activePlayersCount; }

    public void startObservingActivePlayers() {
        regionRepo.observeActivePlayers(new RepositoryCallback<>() {
            @Override
            public void onSuccess(Map<String, Integer> result) { activePlayersCount.setValue(result); }
            @Override
            public void onFailure(Exception e) {}
        });
    }

    public void fetchRegionBorders(Application application) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Map<String, List<GeoPoint>> borders = regionRepo.loadRegions(application.getApplicationContext());
            regionBorderData.postValue(borders);
        });
    }

    public void fetchPlayerDots() {
        regionRepo.fetchPlayerDots(new RepositoryCallback<>() {
            @Override
            public void onSuccess(Map<String, List<GeoPoint>> result) {
                playerDots.setValue(result);
                fetchLeaderboard();
            }
            @Override
            public void onFailure(Exception e) { fetchLeaderboard(); }
        });
    }

    public void fetchLeaderboard() {
        regionRepo.fetchRegionLeaderboard("", new RepositoryCallback<>() {
            @Override
            public void onSuccess(List<Region> result) { leaderboard.setValue(result); }
            @Override
            public void onFailure(Exception e) {}
        });
    }

    public int getDefaultIconColour(String regionKey) { return regionRepo.defaultIconColour(regionKey); }

    private void startAutoRefresh() {
        handler.post(refreshTask);
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacks(refreshTask);
        regionRepo.stopObservingActivePlayers();
        super.onCleared();
    }
}