package com.slagalica.app.repository;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.slagalica.app.R;
import com.slagalica.app.model.Region;
import com.slagalica.app.model.RegionRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class RegionRepository {

    private static final String USERS_COLLECTION = "users";
    private static final String PROFILES_COLLECTION = "profiles";
    private static final String REGIONS_COLLECTION = "regions";
    private static final String CYCLES_COLLECTION = "rankingCycles";
    private static final String ENTRIES_COLLECTION = "entries";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final FirebaseDatabase rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/");
    private final Random rng = new Random();

    private ValueEventListener activePlayersListener;

    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newCachedThreadPool();
    private static final Map<String, List<GeoPoint>> borderCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> regionTotalsCache = new HashMap<>();

    private static final Map<String, Integer> REGION_FILE_MAP;
    static {
        REGION_FILE_MAP = new LinkedHashMap<>();
        REGION_FILE_MAP.put("vojvodina", R.raw.vojvodina_border);
        REGION_FILE_MAP.put("beogradski_region", R.raw.belgrade_border);
        REGION_FILE_MAP.put("sumadija_i_zapadna_srbija", R.raw.sumadija_i_zapad_border);
        REGION_FILE_MAP.put("juzna_i_istocna_srbija", R.raw.jug_i_istok_border);
        REGION_FILE_MAP.put("kosovo_i_metohija", R.raw.kim_border);
    }

    private static final Map<String, double[]> BOUNDS = new HashMap<>();

    public RegionRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public void loadRegions(Context context, RepositoryCallback<List<Region>> cb) {
        db.collection(REGIONS_COLLECTION).get().addOnSuccessListener(snap -> {
            if (!snap.isEmpty()) {
                List<Region> regions = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snap) {
                    Region r = doc.toObject(Region.class);
                    r.setIcon(defaultIcon(r.getRegionKey()));
                    regions.add(r);

                    List<Double> b = r.getBounds();
                    if (b != null && b.size() >= 4)
                        BOUNDS.put(r.getRegionKey(), new double[]{b.get(0), b.get(1), b.get(2), b.get(3)});
                }
                RegionRegistry.populate(regions);
                cb.onSuccess(regions);
                return;
            }
            bootstrapFromLocalGeoJson(context, cb);
        }).addOnFailureListener(cb::onFailure);
    }

    private void bootstrapFromLocalGeoJson(Context context, RepositoryCallback<List<Region>> cb) {
        io.execute(() -> {
            List<Region> results = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : REGION_FILE_MAP.entrySet()) {
                String regionKey = entry.getKey();
                int resourceId = entry.getValue();

                List<GeoPoint> points = loadRegionBorder(context, resourceId);
                if (points.isEmpty()) continue;

                double minLat = 90.0, maxLat = -90.0;
                double minLon = 180.0, maxLon = -180.0;

                for (GeoPoint p : points) {
                    if (p.getLatitude() < minLat) minLat = p.getLatitude();
                    if (p.getLatitude() > maxLat) maxLat = p.getLatitude();
                    if (p.getLongitude() < minLon) minLon = p.getLongitude();
                    if (p.getLongitude() > maxLon) maxLon = p.getLongitude();
                }

                double[] bounds = {minLat, maxLat, minLon, maxLon};
                BOUNDS.put(regionKey, bounds);

                double centreLat = (minLat + maxLat) / 2.0;
                double centreLng = (minLon + maxLon) / 2.0;

                Region info = new Region();
                info.setRegionKey(regionKey);
                info.setDisplayName(keyToDisplayName(regionKey));
                info.setIcon(defaultIcon(regionKey));
                info.setCentreLat(centreLat);
                info.setCentreLng(centreLng);
                info.setBounds(Arrays.asList(minLat, maxLat, minLon, maxLon));

                synchronized (results) { results.add(info); }

                db.collection(REGIONS_COLLECTION).document(regionKey).set(info);
            }

            RegionRegistry.populate(results);
            runOnMainThread(() -> cb.onSuccess(results));
        });
    }

    public void fetchRegionLeaderboard(RepositoryCallback<List<Region>> cb) {
        String cycleId = RankingRepository.currentCycleId("monthly");

        db.collection(CYCLES_COLLECTION).document(cycleId)
                .collection(ENTRIES_COLLECTION)
                .get()
                .addOnSuccessListener(entriesSnap -> {
                    Map<String, Long> starsByUser = new HashMap<>();
                    for (QueryDocumentSnapshot doc : entriesSnap) {
                        Long s = doc.getLong("cycleStars");
                        starsByUser.put(doc.getId(), s != null ? s : 0L);
                    }

                    Map<String, Integer> regionStars = new LinkedHashMap<>();
                    Map<String, Integer> regionActive = new LinkedHashMap<>();
                    Map<String, Integer> regionTotal = new LinkedHashMap<>();

                    for (String key : RegionRegistry.all().keySet()) {
                        regionStars.put(key, 0);
                        regionActive.put(key, 0);
                        regionTotal.put(key, 0);
                    }

                    if (starsByUser.isEmpty()) {
                        fetchTotalCounts(regionStars, regionActive, regionTotal, cb);
                        return;
                    }

                    final int[] pending = {starsByUser.size()};
                    for (Map.Entry<String, Long> e : starsByUser.entrySet()) {
                        String uid = e.getKey();
                        long stars = e.getValue();
                        db.collection(USERS_COLLECTION).document(uid).get()
                                .addOnSuccessListener(userDoc -> {
                                    String region = userDoc.getString("region");
                                    if (region != null)
                                        regionStars.merge(region, (int) stars, Integer::sum);

                                    pending[0]--;
                                    if (pending[0] == 0)
                                        fetchTotalCounts(regionStars, regionActive, regionTotal, cb);

                                })
                                .addOnFailureListener(ex -> {
                                    pending[0]--;
                                    if (pending[0] == 0)
                                        fetchTotalCounts(regionStars, regionActive, regionTotal, cb);
                                });
                    }
                })
                .addOnFailureListener(cb::onFailure);
    }

    private void fetchTotalCounts(Map<String, Integer> regionStars, Map<String, Integer> regionActive, Map<String, Integer> regionTotal, RepositoryCallback<List<Region>> cb) {
        regionTotal.putAll(regionTotalsCache);
        fetchStatsAndPodiums(regionStars, regionActive, regionTotal, cb);
    }

    private void fetchStatsAndPodiums(Map<String, Integer> regionStars, Map<String, Integer> regionActive, Map<String, Integer> regionTotal, RepositoryCallback<List<Region>> cb) {
        db.collection(REGIONS_COLLECTION).get()
                .addOnSuccessListener(statsSnap -> {
                    Map<String, int[]> podiums = new HashMap<>();
                    for (QueryDocumentSnapshot doc : statsSnap) {
                        String regionKey = doc.getId();

                        int gold = doc.getLong("goldCount") != null ? doc.getLong("goldCount").intValue() : 0;
                        int silver = doc.getLong("silverCount") != null ? doc.getLong("silverCount").intValue() : 0;
                        int bronze = doc.getLong("bronzeCount") != null ? doc.getLong("bronzeCount").intValue() : 0;
                        podiums.put(regionKey, new int[]{gold, silver, bronze});
                    }
                    cb.onSuccess(assembleLeaderboard(regionStars, regionActive, regionTotal, podiums));
                })
                .addOnFailureListener(ex -> cb.onSuccess(assembleLeaderboard(regionStars, regionActive, regionTotal, new HashMap<>())));
    }

    private List<Region> assembleLeaderboard(Map<String, Integer> stars, Map<String, Integer> active, Map<String, Integer> total, Map<String, int[]>   podiums) {
        List<Region> list = new ArrayList<>();
        for (Map.Entry<String, Region> entry : RegionRegistry.all().entrySet()) {
            String key = entry.getKey();
            Region info = new Region(
                    entry.getValue().getRegionKey(),
                    entry.getValue().getDisplayName(),
                    entry.getValue().getIcon(),
                    entry.getValue().getCentreLat(),
                    entry.getValue().getCentreLng()
            );
            info.setCycleStars(stars.getOrDefault(key, 0));
            info.setActivePlayers(active.getOrDefault(key, 0));
            info.setTotalPlayers(total.getOrDefault(key, 0));

            int[] p = podiums.getOrDefault(key, new int[]{0, 0, 0});
            info.setGoldCount(p[0]);
            info.setSilverCount(p[1]);
            info.setBronzeCount(p[2]);
            list.add(info);
        }
        Collections.sort(list, (a, b) -> b.getCycleStars() - a.getCycleStars());
        for (int i = 0; i < list.size(); i++) list.get(i).setRank(i + 1);
        return list;
    }

    public void fetchPlayerDots(RepositoryCallback<Map<String, List<GeoPoint>>> cb) {
        db.collection(USERS_COLLECTION).get()
                .addOnSuccessListener(snap -> {
                    Map<String, List<GeoPoint>> dots = new HashMap<>();
                    Map<String, Integer> totals = new HashMap<>();

                    for (QueryDocumentSnapshot doc : snap) {
                        String region = doc.getString("region");
                        if (region == null) continue;

                        totals.merge(region, 1, Integer::sum);

                        double[] bbox = BOUNDS.get(region);
                        if (bbox == null) continue;

                        List<GeoPoint> polygon = borderCache.get(region);
                        double lat, lng;
                        GeoPoint randomPoint;
                        int attempts = 0;

                        do {
                            lat = bbox[0] + rng.nextDouble() * (bbox[1] - bbox[0]);
                            lng = bbox[2] + rng.nextDouble() * (bbox[3] - bbox[2]);
                            randomPoint = new GeoPoint(lat, lng);
                            attempts++;
                        } while (polygon != null && !isPointInPolygon(randomPoint, polygon) && attempts < 100);

                        dots.computeIfAbsent(region, k -> new ArrayList<>()).add(randomPoint);
                    }
                    regionTotalsCache.clear();
                    regionTotalsCache.putAll(totals);

                    cb.onSuccess(dots);
                })
                .addOnFailureListener(cb::onFailure);
    }

    public void distributeRegionPodium(List<Region> sortedRegions, RepositoryCallback<Void> cb) {
        String[] fields = {"goldCount", "silverCount", "bronzeCount"};
        for (int i = 0; i < Math.min(3, sortedRegions.size()); i++) {
            String key = sortedRegions.get(i).getRegionKey();
            String field = fields[i];

            db.collection(REGIONS_COLLECTION).document(key)
                    .update(field, com.google.firebase.firestore.FieldValue.increment(1))
                    .addOnFailureListener(e -> {
                        Map<String, Object> init = new HashMap<>();
                        init.put("goldCount", 0); init.put("silverCount", 0); init.put("bronzeCount", 0);
                        db.collection(REGIONS_COLLECTION).document(key).set(init)
                                .addOnSuccessListener(v ->
                                        db.collection(REGIONS_COLLECTION).document(key)
                                                .update(field, com.google.firebase.firestore.FieldValue.increment(1)));
                    });
        }

        db.collection(USERS_COLLECTION).get().addOnSuccessListener(snap -> {
            for (QueryDocumentSnapshot doc : snap) {
                String uid = doc.getId();
                String region = doc.getString("region");
                int pRank = 0;
                for (int i = 0; i < Math.min(3, sortedRegions.size()); i++) {
                    if (sortedRegions.get(i).getRegionKey().equals(region)) {
                        pRank = i + 1; break;
                    }
                }
                db.collection(PROFILES_COLLECTION).document(uid).update("prevCycleRegionRank", pRank);
            }
            cb.onSuccess(null);
        }).addOnFailureListener(cb::onFailure);
    }

    public void fetchCurrentUserRegion(RepositoryCallback<String> cb) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) { cb.onSuccess(null); return; }
        db.collection(USERS_COLLECTION).document(uid).get()
                .addOnSuccessListener(doc -> cb.onSuccess(doc.getString("region")))
                .addOnFailureListener(cb::onFailure);
    }

    public void observeActivePlayers(RepositoryCallback<Map<String, Integer>> cb) {
        if (activePlayersListener != null) return;

        activePlayersListener = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                Map<String, Integer> activeCounts = new HashMap<>();

                for (String key : REGION_FILE_MAP.keySet())
                    activeCounts.put(key, 0);

                for (com.google.firebase.database.DataSnapshot userSnap : snapshot.getChildren()) {
                    String status = userSnap.child("status").getValue(String.class);
                    if ("online".equals(status) || "in_game".equals(status)) {
                        String region = userSnap.child("region").getValue(String.class);
                        if (region != null && activeCounts.containsKey(region)) {
                            activeCounts.merge(region, 1, Integer::sum);
                        }
                    }
                }
                cb.onSuccess(activeCounts);
            }
            @Override
            public void onCancelled(com.google.firebase.database.DatabaseError error) {
                cb.onFailure(error.toException());
            }
        };

        rtdb.getReference("presence").addValueEventListener(activePlayersListener);
    }

    public void stopObservingActivePlayers() {
        if (activePlayersListener != null) {
            rtdb.getReference("presence").removeEventListener(activePlayersListener);
            activePlayersListener = null;
        }
    }

    private String keyToDisplayName(String key) {
        switch (key) {
            case "beogradski_region": return "Beogradski region";
            case "vojvodina": return "Vojvodina";
            case "sumadija_i_zapadna_srbija": return "Šumadija i Zapadna Srbija";
            case "juzna_i_istocna_srbija": return "Južna i Istočna Srbija";
            case "kosovo_i_metohija": return "Kosovo i Metohija";
            default: return key;
        }
    }

    private int defaultIcon(String key) {
        switch (key) {
            case "beogradski_region": return R.drawable.ic_building;
            case "vojvodina": return R.drawable.ic_grass;
            case "sumadija_i_zapadna_srbija": return R.drawable.ic_forest;
            case "juzna_i_istocna_srbija": return R.drawable.ic_mountain;
            case "kosovo_i_metohija": return R.drawable.ic_monastery;
            default: return R.drawable.ic_place;
        }
    }

    public int defaultIconColour(String key) {
        switch (key) {
            case "beogradski_region": return R.color.building;
            case "vojvodina": return R.color.grass;
            case "sumadija_i_zapadna_srbija": return R.color.forest;
            case "juzna_i_istocna_srbija": return R.color.mountain;
            case "kosovo_i_metohija": return R.color.monastery;
            default: return R.color.text_mute;
        }
    }

    public List<GeoPoint> loadRegionBorder(Context context, int resourceId) {
        List<GeoPoint> points = new ArrayList<>();
        try (InputStream is = context.getResources().openRawResource(resourceId)) {
            String jsonString = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

            JSONObject jsonObject = new JSONObject(jsonString);
            JSONObject geometry = jsonObject.getJSONObject("geometry");

            String type = geometry.getString("type");
            JSONArray coordinates;

            if ("Polygon".equals(type))
                coordinates = geometry.getJSONArray("coordinates").getJSONArray(0);
            else if ("MultiPolygon".equals(type))
                coordinates = geometry.getJSONArray("coordinates").getJSONArray(0).getJSONArray(0);
            else
                return points;

            for (int i = 0; i < coordinates.length(); i++) {
                JSONArray coord = coordinates.getJSONArray(i);
                points.add(new GeoPoint(coord.getDouble(1), coord.getDouble(0)));
            }
        } catch (Exception e) {}

        return points;
    }

    public Map<String, List<GeoPoint>> loadRegions(Context context) {
        Map<String, List<GeoPoint>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : REGION_FILE_MAP.entrySet()) {
            List<GeoPoint> border = loadRegionBorder(context, entry.getValue());
            result.put(entry.getKey(), border);
            borderCache.put(entry.getKey(), border);
        }
        return result;
    }

    private boolean isPointInPolygon(GeoPoint point, List<GeoPoint> polygon) {
        int intersections = 0;
        double x = point.getLongitude();
        double y = point.getLatitude();
        int n = polygon.size();

        for (int i = 0; i < n; i++) {
            GeoPoint p1 = polygon.get(i);
            GeoPoint p2 = polygon.get((i + 1) % n);

            if (p1.getLatitude() == p2.getLatitude()) continue;
            if (y < Math.min(p1.getLatitude(), p2.getLatitude())) continue;
            if (y >= Math.max(p1.getLatitude(), p2.getLatitude())) continue;

            double xcalc = p1.getLongitude() + (y - p1.getLatitude()) * (p2.getLongitude() - p1.getLongitude()) / (p2.getLatitude() - p1.getLatitude());
            if (xcalc > x) intersections++;
        }
        return (intersections % 2 != 0);
    }

    private void runOnMainThread(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
}