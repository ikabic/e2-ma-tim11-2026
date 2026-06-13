package com.slagalica.app.ui.regions;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.slagalica.app.R;
import com.slagalica.app.databinding.FragmentRegionBinding;
import com.slagalica.app.model.Region;
import com.slagalica.app.ui.challenge.ChallengeActivity;
import com.slagalica.app.viewmodel.RegionViewModel;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionFragment extends Fragment {

    private FragmentRegionBinding binding;
    private RegionViewModel vm;

    static final double SERBIA_SOUTH = 41.85;
    static final double SERBIA_NORTH = 46.19;
    static final double SERBIA_WEST = 18.82;
    static final double SERBIA_EAST = 23.01;

    private final Map<String, Region> regionStatsMap = new HashMap<>();
    private String selectedRegionKey = null;
    private Polygon selectedPolygon = null;
    private final Map<String, Integer> cachedActiveCounts = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRegionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vm = new ViewModelProvider(requireActivity()).get(RegionViewModel.class);
        vm.bootstrapRegions(requireContext().getApplicationContext());
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        vm.getRegionBorderData().observe(getViewLifecycleOwner(), this::drawRegionBorders);

        vm.fetchRegionBorders(requireActivity().getApplication());

        setupMap();
        setupDetailCard();
        observeViewModel();

        binding.btnSendChallenge.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ChallengeActivity.class)));
    }

    private void observeViewModel() {
        vm.getLeaderboard().observe(getViewLifecycleOwner(), regions -> {
            if (regions == null) return;

            regionStatsMap.clear();
            for (Region r : regions) {
                if (cachedActiveCounts.containsKey(r.getRegionKey()))
                    r.setActivePlayers(cachedActiveCounts.get(r.getRegionKey()));
                regionStatsMap.put(r.getRegionKey(), r);
            }

            if (selectedRegionKey != null && regionStatsMap.containsKey(selectedRegionKey))
                showRegionDetail(regionStatsMap.get(selectedRegionKey));
        });

        vm.getPlayerDots().observe(getViewLifecycleOwner(), dots -> {
            if (dots != null) plotPlayerDots(dots);
        });

        vm.getActivePlayersCount().observe(getViewLifecycleOwner(), activeMap -> {
            if (activeMap == null) return;

            cachedActiveCounts.clear();
            cachedActiveCounts.putAll(activeMap);

            for (Map.Entry<String, Integer> entry : activeMap.entrySet()) {
                Region r = regionStatsMap.get(entry.getKey());
                if (r != null)
                    r.setActivePlayers(entry.getValue());
            }

            if (selectedRegionKey != null && activeMap.containsKey(selectedRegionKey))
                binding.tvDetailActive.setText(String.valueOf(activeMap.get(selectedRegionKey)));
        });
    }

    private void setupMap() {
        MapView map = binding.mapView;
        map.setTileSource(new XYTileSource("CartoDarkMatter", 6, 14, 256, ".png",
                new String[] {
                        "https://a.basemaps.cartocdn.com/dark_all/",
                        "https://b.basemaps.cartocdn.com/dark_all/",
                        "https://c.basemaps.cartocdn.com/dark_all/"
                },
                "© OpenStreetMap contributors, © CartoDB"
        ));

        map.setMultiTouchControls(true);
        map.setMinZoomLevel(8.1);
        map.setMaxZoomLevel(11.0);
        map.setScrollableAreaLimitDouble(new BoundingBox(SERBIA_NORTH, SERBIA_EAST, SERBIA_SOUTH, SERBIA_WEST));
        map.getController().setZoom(8.1);
        map.getController().setCenter(new GeoPoint(44.0, 21.0));

        map.setBuiltInZoomControls(false);
        binding.fabZoomIn.setOnClickListener(v -> map.getController().zoomIn());
        binding.fabZoomOut.setOnClickListener(v -> map.getController().zoomOut());
    }

    private void drawRegionBorders(Map<String, List<GeoPoint>> borderMap) {
        MapView map = binding.mapView;

        for (Map.Entry<String, List<GeoPoint>> entry : borderMap.entrySet()) {
            String regionKey = entry.getKey();
            List<GeoPoint> borders = entry.getValue();

            Polygon polygon = new Polygon();
            polygon.setPoints(borders);
            polygon.getOutlinePaint().setColor(ContextCompat.getColor(requireContext(), R.color.accent));
            polygon.getOutlinePaint().setStrokeWidth(4.0f);

            polygon.setOnClickListener((p, mapView, eventPos) -> {
                onRegionPolygonTapped(p, borders, regionKey);
                return true;
            });

            map.getOverlays().add(polygon);
        }

        map.invalidate();
    }

    private void onRegionPolygonTapped(Polygon tapped, List<GeoPoint> borders, String regionKey) {
        MapView map = binding.mapView;

        if (selectedPolygon != null) {
            selectedPolygon.getFillPaint().setColor(Color.TRANSPARENT);
            selectedPolygon.getOutlinePaint().setColor(ContextCompat.getColor(requireContext(), R.color.accent));
        }

        tapped.getFillPaint().setColor(ContextCompat.getColor(requireContext(), R.color.accent_alpha));
        selectedPolygon = tapped;
        selectedRegionKey = regionKey;
        map.invalidate();

        GeoPoint centroid = computeCentroid(borders);
        map.getController().animateTo(centroid);

        Region region = regionStatsMap.get(regionKey);
        if (region != null) showRegionDetail(region);
        else vm.fetchLeaderboard();
    }

    private void plotPlayerDots(Map<String, List<GeoPoint>> dots) {
        MapView map = binding.mapView;
        map.getOverlays().removeIf(o -> o instanceof Marker && ((Marker) o).getId() == null);

        Drawable sizedDot = getScaledMarkerIcon(R.drawable.ic_player_dot);

        for (Map.Entry<String, List<GeoPoint>> entry : dots.entrySet()) {
            for (GeoPoint pt : entry.getValue()) {
                Marker m = new Marker(map);
                m.setPosition(pt);
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                if (sizedDot != null) m.setIcon(sizedDot);
                m.setInfoWindow(null);
                map.getOverlays().add(m);
            }
        }
        map.invalidate();
    }

    private Drawable getScaledMarkerIcon(int resId) {
        Drawable drawable = ContextCompat.getDrawable(requireContext(), resId);
        if (drawable == null) return null;

        int originalWidth = drawable.getIntrinsicWidth();
        int originalHeight = drawable.getIntrinsicHeight();

        int targetWidth = originalWidth / 2;
        int targetHeight = originalHeight / 2;

        if (targetWidth <= 0 || targetHeight <= 0) {
            targetWidth = 32;
            targetHeight = 32;
        }

        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return new BitmapDrawable(getResources(), bitmap);
    }

    private void setupDetailCard() {
        binding.cardRegionDetail.setVisibility(View.GONE);
        binding.btnCloseDetail.setOnClickListener(v -> {
            binding.cardRegionDetail.setVisibility(View.GONE);
            selectedRegionKey = null;

            if (selectedPolygon != null) {
                selectedPolygon.getFillPaint().setColor(Color.TRANSPARENT);
                binding.mapView.invalidate();
                selectedPolygon = null;
            }
        });
    }

    private void showRegionDetail(Region region) {
        binding.heroDetail.setBackgroundResource(vm.getDefaultIconColour(region.getRegionKey()));
        binding.ivDetailIcon.setImageResource(region.getIcon());
        binding.ivDetailIcon.setImageTintList(AppCompatResources.getColorStateList(requireContext(), vm.getDefaultIconColour(region.getRegionKey())));
        binding.tvDetailName.setText(region.getDisplayName());

        if (rankLabel(region.getRank()).first != null) {
            binding.tvDetailSubtitleIcon.setVisibility(View.VISIBLE);
            binding.tvDetailSubtitleIcon.setImageResource(rankLabel(region.getRank()).first.first);
            binding.tvDetailSubtitleIcon.setImageTintList(AppCompatResources.getColorStateList(requireContext(), rankLabel(region.getRank()).first.second));
        } else binding.tvDetailSubtitleIcon.setVisibility(View.GONE);
        binding.tvDetailSubtitle.setText(rankLabel(region.getRank()).second);

        binding.tvDetailActive.setText(String.valueOf(region.getActivePlayers()));
        binding.tvDetailTotal.setText(String.valueOf(region.getTotalPlayers()));
        binding.tvDetailStars.setText(String.valueOf(region.getCycleStars()));
        binding.tvDetailGold.setText(String.valueOf(region.getGoldCount()));
        binding.tvDetailSilver.setText(String.valueOf(region.getSilverCount()));
        binding.tvDetailBronze.setText(String.valueOf(region.getBronzeCount()));

        binding.cardRegionDetail.setVisibility(View.VISIBLE);
    }

    private GeoPoint computeCentroid(List<GeoPoint> points) {
        double lat = 0, lon = 0;
        for (GeoPoint p : points) {
            lat += p.getLatitude();
            lon += p.getLongitude();
        }
        return new GeoPoint(lat / points.size(), lon / points.size());
    }

    private Pair<Pair<Integer, Integer>, String> rankLabel(int rank) {
        if (rank == 1) return Pair.create(Pair.create(R.drawable.ic_top_award, R.color.gold), "1st place");
        if (rank == 2) return Pair.create(Pair.create(R.drawable.ic_award, R.color.silver),"2nd place");
        if (rank == 3) return Pair.create(Pair.create(R.drawable.ic_award, R.color.bronze), "3rd place");
        return Pair.create(null, rank + "th place");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) binding.mapView.onResume();
    }

    @Override
    public void onPause() {
        if (binding != null) binding.mapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}